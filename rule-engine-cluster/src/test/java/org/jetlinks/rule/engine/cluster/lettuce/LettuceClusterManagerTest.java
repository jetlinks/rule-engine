package org.jetlinks.rule.engine.cluster.lettuce;

import lombok.SneakyThrows;
import org.jetlinks.lettuce.LettucePlus;
import org.jetlinks.lettuce.supports.DefaultLettucePlus;
import org.jetlinks.rule.engine.api.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

public class LettuceClusterManagerTest {


    LettuceHaManager haManager;

    ClusterManager clusterManager;


    @Before
    public void init() {

        LettucePlus plus = DefaultLettucePlus.standalone(RedisClientHelper.createRedisClient());
        clusterManager = new LettuceClusterManager(plus);

        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId("test");
        haManager = new LettuceHaManager(nodeInfo, plus.getHaManager("test")){
            @Override
            public void startup() {
                haManager.startup(convert(getCurrentNode()));
            }
        };
        haManager.startup();

        Assert.assertFalse(haManager.getAllAliveNode().isEmpty());

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
    public void testQueue() {


    }

    @Test
    @SneakyThrows
    public void testTopic() {
        AtomicReference<String> data = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        latch.await(10, TimeUnit.SECONDS);

        Assert.assertEquals(data.get(), "test");
    }

}