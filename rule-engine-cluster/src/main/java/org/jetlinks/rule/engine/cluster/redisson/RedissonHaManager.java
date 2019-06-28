package org.jetlinks.rule.engine.cluster.redisson;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.ha.ClusterNotify;
import org.jetlinks.rule.engine.api.cluster.ha.ClusterNotifyReply;
import org.jetlinks.rule.engine.api.cluster.ha.HaManager;
import org.redisson.api.*;
import org.springframework.util.Assert;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Slf4j
@SuppressWarnings("all")
public class RedissonHaManager implements HaManager {

    @Getter
    @Setter
    private NodeInfo currentNode;

    @Getter
    @Setter
    private RedissonClient redissonClient;

    @Getter
    @Setter
    private ScheduledExecutorService executorService;

    @Getter
    @Setter
    private String clusterName = "default.ha";

    protected RPatternTopic clusterNodeTopic;

    protected RTopic clusterNodeLeaveTopic;

    protected Map<String, Function> notifyListener = new ConcurrentHashMap<>();

    @Setter
    @Getter
    private long timeToLeave = 10;

    private RMap<String, NodeInfo> allNodeInfo;
    private Map<String, NodeInfo> localAllNode;

    protected void doNodeJoin(NodeInfo nodeInfo) {
        if (nodeInfo.getId().equals(currentNode.getId())) {
            return;
        }
        nodeInfo.setLastKeepAliveTime(System.currentTimeMillis());
        localAllNode.put(nodeInfo.getId(), nodeInfo);
        allNodeInfo.put(nodeInfo.getId(), nodeInfo);
        joinConsumer.accept(nodeInfo);
    }

    protected void doNodeLeave(NodeInfo nodeInfo) {
        if (nodeInfo.getId().equals(currentNode.getId())) {
            return;
        }
        localAllNode.remove(nodeInfo.getId());
        allNodeInfo.fastRemove(nodeInfo.getId());
        leaveConsumer.accept(nodeInfo);
    }


    protected String getRedisKey(String key) {

        return clusterName + ":" + key;
    }

    public void start() {
        Assert.notNull(redissonClient, "redissonClient");
        Assert.notNull(currentNode, "currentNode");
        Assert.notNull(currentNode.getId(), "currentNode.id");
        Assert.notNull(executorService, "executorService");

        notifyListener.put("ping", (o) -> "pong");

        allNodeInfo = redissonClient.getMap(getRedisKey("cluster:nodes"));
        //注册自己
        allNodeInfo.put(currentNode.getId(), currentNode);

        localAllNode = new HashMap<>(allNodeInfo);

        clusterNodeTopic = redissonClient.getPatternTopic(getRedisKey("cluster:node:*"));
        clusterNodeLeaveTopic = redissonClient.getTopic(getRedisKey("cluster:node:leave"));
        RTopic keepAlive = redissonClient.getTopic(getRedisKey("cluster:node:keep"));
        //集群通知
        redissonClient.getTopic(getRedisKey("cluster:notify:" + currentNode.getId()))
                .addListener(ClusterNotify.class, (channel, msg) -> {
                    Function<Object, Object> handler = Optional.ofNullable(notifyListener.get(msg.getAddress()))
                            .orElseGet(() -> (obj -> {
                                throw new UnsupportedOperationException("unsupported address:".concat(msg.getAddress()));
                            }));
                    String replyId = msg.getReplyId();
                    ClusterNotifyReply reply = new ClusterNotifyReply();
                    reply.setReplyId(replyId);
                    try {
                        Object result = handler.apply(msg.getMessage());
                        reply.setSuccess(true);
                        reply.setReply(result);
                    } catch (Throwable error) {
                        log.error("execute notify error : " + error.getMessage(), error);
                        reply.setErrorType(error.getClass().getName());
                        reply.setErrorMessage(error.getMessage());
                    }
                    redissonClient.getBucket("notify:result:" + replyId).set(reply, 1, TimeUnit.MINUTES);
                    RSemaphore rSemaphore = redissonClient.getSemaphore("notify:" + replyId);
                    rSemaphore.release();
                    rSemaphore.expire(1, TimeUnit.MINUTES);
                });

        //订阅节点上下线
        clusterNodeTopic.addListener(NodeInfo.class, (pattern, channel, msg) -> {
            String operation = String.valueOf(channel);
            if (getRedisKey("cluster:node:join").equals(operation)) {
                doNodeJoin(msg);
            } else if (getRedisKey("cluster:node:leave").equals(operation)) {
                doNodeLeave(msg);
            } else if (getRedisKey("cluster:node:keep").equals(operation)) {
                NodeInfo nodeInfo = localAllNode.get(msg.getId());
                if (nodeInfo == null) {
                    doNodeJoin(msg);
                } else {
                    nodeInfo.setLastKeepAliveTime(System.currentTimeMillis());
                }
            } else {
                log.info("unknown channel:{} {}", operation, msg);
            }
        });

        executorService.scheduleAtFixedRate(() -> {
            //保活
            currentNode.setLastKeepAliveTime(System.currentTimeMillis());
            //注册自己
            allNodeInfo.put(currentNode.getId(), currentNode);
            keepAlive.publish(currentNode);

            for (NodeInfo value : localAllNode.values()) {
                sendNotify(value.getId(), "ping", System.currentTimeMillis())
                        .whenCompleteAsync((pong, err) -> {
                            if (!"pong".equals(pong)) {
                                clusterNodeLeaveTopic.publishAsync(value);
                            }
                        });
            }

        }, 5, Math.min(2, timeToLeave), TimeUnit.SECONDS);
    }

    public void shutdown() {
        clusterNodeLeaveTopic.publish(currentNode);
    }

    @Override
    public List<NodeInfo> getAllAliveNode() {
        return new ArrayList<>(localAllNode.values());
    }

    private volatile Consumer<NodeInfo> joinConsumer = (info) -> log.info("node join:{}", info);

    private volatile Consumer<NodeInfo> leaveConsumer = (info) -> log.info("node leave:{}", info);

    @Override
    public synchronized HaManager onNodeJoin(Consumer<NodeInfo> consumer) {
        joinConsumer = joinConsumer.andThen(consumer);
        return this;
    }

    @Override
    public synchronized HaManager onNodeLeave(Consumer<NodeInfo> consumer) {
        leaveConsumer = leaveConsumer.andThen(consumer);
        return this;
    }

    @Override
    @SuppressWarnings("all")
    public <T, R> void onNotify(String address, Function<T, R> consumer) {
        notifyListener.compute(address, (key, old) -> {
            if (old == null) {
                return consumer;
            }
            return t -> {
                old.apply(t);
                return consumer.apply((T) t);
            };
        });
    }

    @Override
    public <V> CompletionStage<V> sendNotify(String nodeId, String address, Object message) {
        String replyId = IDGenerator.MD5.generate();
        return redissonClient.getTopic(getRedisKey("cluster:notify:".concat(nodeId)))
                .publishAsync(new ClusterNotify(replyId, address, message))
                .thenCompose(len -> {
                    if (len <= 0) {
                        return CompletableFuture.completedFuture(null);
                    }
                    RSemaphore rSemaphore = redissonClient.getSemaphore("notify:" + replyId);
                    return rSemaphore
                            .tryAcquireAsync(len.intValue(), 10, TimeUnit.MINUTES)
                            .thenCompose(success -> {
                                rSemaphore.delete();
                                CompletableFuture<V> future = new CompletableFuture<>();
                                redissonClient.<ClusterNotifyReply>getBucket("notify:result:" + replyId)
                                        .getAndDeleteAsync()
                                        .whenComplete((clusterNotifyReply, throwable) -> {
                                            if (null != throwable) {
                                                future.completeExceptionally(throwable);
                                            } else if (clusterNotifyReply.isSuccess()) {
                                                future.complete((V) clusterNotifyReply.getReply());
                                            } else if (clusterNotifyReply != null) {
                                                future.completeExceptionally(new RuntimeException(clusterNotifyReply.getErrorType() + ":" + clusterNotifyReply.getErrorMessage()));
                                            }
                                        });
                                return future;
                            });
                });

    }
}
