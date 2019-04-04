package org.jetlinks.rule.engine.cluster.redisson;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.cluster.ClusterLock;
import org.jetlinks.rule.engine.cluster.ClusterSemaphore;
import org.jetlinks.rule.engine.cluster.NodeInfo;
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
    private RedissonClient         redissonClient = RedissonHelper.newRedissonClient();
    private RedissonHaManager      haManager;

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

        clusterManager = new RedissonClusterManager();
        clusterManager.setRedissonClient(redissonClient);
        clusterManager.setHaManager(haManager);
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
        CountDownLatch count = new CountDownLatch(1);
        AtomicReference<String> reference = new AtomicReference<>();
        haManager.<String>onNotify("test", msg -> {
            reference.set(msg);
            count.countDown();
        });

        haManager.sendNotify(haManager.getCurrentNode().getId(), "test", "test-message");
        count.await(10, TimeUnit.SECONDS);
        Assert.assertEquals(reference.get(), "test-message");
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
        haManager.clusterNodeKeepTopic.publish(nodeInfo);
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

    @Test
    @SneakyThrows
    public void testMap() {
        clusterManager.getMap("test")
                .put("test", "test");

        Assert.assertEquals(clusterManager.getMap("test").get("test").orElse(null), "test");

        Assert.assertEquals(clusterManager.getMap("test").removeAsync("test").toCompletableFuture().get(), "test");

        Assert.assertNull(clusterManager.getMap("test").getAsync("test").toCompletableFuture().get());

        Assert.assertNotNull(clusterManager.getMap("test").toMap());

    }

    @Test
    @SuppressWarnings("all")
    public void testLock() {
        for (int i = 0; i < 10; i++) {
            ClusterLock lock = clusterManager.getLock("test", 5, TimeUnit.SECONDS);
            StringBuilder builder = new StringBuilder();

            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                builder.append("2");
                lock.unlock();
            }).start();
            builder.append("1");
            clusterManager.getLock("test", 1, TimeUnit.SECONDS).unlock();
            builder.append("3");

            Assert.assertEquals(builder.toString(), "123");
            System.out.println(builder + "--" + i);
        }
    }

    @Test
    @SuppressWarnings("all")
    @SneakyThrows
    public void testSemaphore() {
        for (int i = 0; i < 10; i++) {
            ClusterSemaphore semaphore = clusterManager.getSemaphore("test", 0);
            StringBuilder builder = new StringBuilder();
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
                builder.append("2");
                semaphore.release();
            }).start();
            builder.append("1");
            semaphore.tryAcquire(10, TimeUnit.SECONDS);
            clusterManager
                    .getSemaphore("test", 1)
                    .tryAcquireAsync(1, TimeUnit.SECONDS)
                    .thenRunAsync(() -> {
                        clusterManager.getSemaphore("test", 1).release();
                    })
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);
            builder.append("3");

            Assert.assertEquals(builder.toString(), "123");
            System.out.println(builder + "--" + i);
        }
    }
}