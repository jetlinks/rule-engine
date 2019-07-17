package org.jetlinks.rule.engine.cluster.lettuce;

import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.lettuce.RedisHaManager;
import org.jetlinks.lettuce.ServerNodeInfo;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.ha.HaManager;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

public class LettuceHaManager implements HaManager {

    private RedisHaManager haManager;

    private NodeInfo current;

    public LettuceHaManager(NodeInfo current, RedisHaManager haManager) {
        this.current = current;
        this.haManager = haManager;
    }

    public void startup() {

        haManager.startup(convert(current), TimeUnit.SECONDS.toMillis(10));
    }

    @Override
    public NodeInfo getCurrentNode() {
        return current;
    }

    @Override
    public List<NodeInfo> getAllAliveNode() {
        return haManager.getAllNode()
                .stream()
                .map(this::convert)
                .collect(Collectors.toList());
    }

    @Override
    public HaManager onNodeJoin(Consumer<NodeInfo> consumer) {
        haManager.onNodeJoin(nodeInfo -> consumer.accept(convert(nodeInfo)));
        return this;
    }

    @Override
    public HaManager onNodeLeave(Consumer<NodeInfo> consumer) {
        haManager.onNodeLeave(nodeInfo -> consumer.accept(convert(nodeInfo)));
        return this;
    }

    @Override
    public <T, R> void onNotify(String address, Function<T, R> consumer) {
        haManager.onNotify(address, Object.class, (data) -> {
            return CompletableFuture.completedFuture(consumer.apply((T) data));
        });
    }

    @Override
    public <V> CompletionStage<V> sendNotify(String nodeId, String address, Object message) {
        return haManager.sendNotifyReply(nodeId, address, message, Duration.ofSeconds(30));
    }

    @Override
    public void sendNotifyNoReply(String nodeId, String address, Object message) {
        haManager.sendNotify(nodeId, address, message);
    }

    private NodeInfo convert(ServerNodeInfo nodeInfo) {
        NodeInfo info = new NodeInfo();
        info.setId(nodeInfo.getId());

//        if (MapUtils.isNotEmpty(nodeInfo.getProperties())) {
//            FastBeanCopier.copy(nodeInfo.getProperties(), info);
//        }

        return info;
    }

    private ServerNodeInfo convert(NodeInfo nodeInfo) {
        ServerNodeInfo info = new ServerNodeInfo();
        info.setId(nodeInfo.getId());

        info.setProperties(FastBeanCopier.copy(nodeInfo, new HashMap<>()));

        return info;
    }

    public void shutdown(){
        haManager.shutdown();
    }
}
