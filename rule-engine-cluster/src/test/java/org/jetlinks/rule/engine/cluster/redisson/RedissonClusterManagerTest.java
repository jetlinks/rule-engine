package org.jetlinks.rule.engine.cluster.redisson;

import lombok.SneakyThrows;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonClusterManagerTest {

    private RedissonClusterManager clusterManager;
    private RedissonClient redissonClient = RedissonHelper.newRedissonClient();
    private RedissonHaManager haManager;

    @Before
    public void init() {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId("test");
        nodeInfo.setUptime(System.currentTimeMillis());
        haManager = new RedissonHaManager();
        haManager.setCurrentNode(nodeInfo);
        haManager.setExecutorService(Executors.newScheduledThreadPool(5));
        haManager.setRedissonClient(redissonClient);
        haManager.setTimeToLeave(5);
        haManager.setClusterName("cluster:" + IDGenerator.MD5.generate());

        clusterManager = new RedissonClusterManager();
        clusterManager.setRedissonClient(redissonClient);
        clusterManager.setExecutorService(Executors.newScheduledThreadPool(5));
        clusterManager.setHaManager(haManager);
        clusterManager.start();
        clusterManager.setName(haManager.getClusterName());
        haManager.start();
    }

    @After
    public void after() {
        clusterManager.shutdown();
        haManager.shutdown();
    }

    @Test
    @SneakyThrows
    public void testHaMessage() {
        haManager.<String, String>onNotify("test", msg -> "test-message-reply");

        String result = haManager.<String>sendNotify(haManager.getCurrentNode().getId(), "test", "test-message")
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        Assert.assertEquals(result, "test-message-reply");
    }

    @Test
    @SneakyThrows
    public void testHa() {
        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId("test2");
        nodeInfo.setLastKeepAliveTime(System.currentTimeMillis());
        AtomicLong counter = new AtomicLong();
        clusterManager.getHaManager()
                .onNodeJoin(node -> {
                    counter.incrementAndGet();
                    System.out.println("join:" + node);
                })
                .onNodeLeave(node -> {
                    counter.decrementAndGet();
                    System.out.println("leave:" + node);
                });
        int oldSize = clusterManager.getAllAliveNode().size();
        redissonClient.getTopic(haManager.getRedisKey("cluster:node:join"))
                .publish(nodeInfo);

        Thread.sleep(1000);
        Assert.assertEquals(counter.get(), 1);
        Assert.assertEquals(clusterManager.getAllAliveNode().size(), oldSize + 1);
        //等待，让test2失效
        Thread.sleep(8000);
        Assert.assertEquals(counter.get(), 0);
        Assert.assertEquals(clusterManager.getAllAliveNode().size(), oldSize);

    }

    @Test
    @SneakyThrows
    public void testQueue() {
        int queueSize = 200;
        int dataSize = 10;
        AtomicLong counter = new AtomicLong();
        CountDownLatch downLatch = new CountDownLatch(queueSize * dataSize);
        for (int i = 0; i < queueSize; i++) {
            clusterManager.<String>getQueue("test" + i)
                    .acceptOnce(data -> {
                        counter.incrementAndGet();
                        downLatch.countDown();
                    });
        }

        RedissonClient redissonClient = RedissonHelper.newRedissonClient();
        for (int i = 0; i < queueSize; i++) {
            RQueue<String> queue = redissonClient.getQueue(clusterManager.getRedisKey("queue", "test" + i));
            for (int i1 = 0; i1 < dataSize; i1++) {
                queue.add("t" + i1);
            }
        }
        downLatch.await(60, TimeUnit.SECONDS);
        Assert.assertEquals(counter.get(), dataSize * queueSize);
    }

    @Test
    @SneakyThrows
    public void testTopic() {
        AtomicReference<String> data = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        clusterManager.getTopic(String.class, "test")
                .addListener(str -> {
                    data.set(str);
                    latch.countDown();
                });

        clusterManager.getTopic(String.class, "test").publish("test");

        latch.await(10, TimeUnit.SECONDS);

        Assert.assertEquals(data.get(), "test");
    }
}