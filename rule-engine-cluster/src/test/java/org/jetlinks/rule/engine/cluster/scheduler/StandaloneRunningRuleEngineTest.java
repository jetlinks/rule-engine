package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.SneakyThrows;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.model.*;
import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.cluster.redisson.RedissonClusterManager;
import org.jetlinks.rule.engine.cluster.redisson.RedissonHaManager;
import org.jetlinks.rule.engine.cluster.redisson.RedissonHelper;
import org.jetlinks.rule.engine.cluster.repository.MockRuleInstanceRepository;
import org.jetlinks.rule.engine.cluster.worker.RuleEngineWorker;
import org.jetlinks.rule.engine.executor.DefaultExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.executor.supports.JavaMethodInvokeStrategy;
import org.jetlinks.rule.engine.model.xml.XmlRuleModelParserStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.jetlinks.rule.engine.api.RuleDataHelper.newHelper;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class StandaloneRunningRuleEngineTest {
    private RedissonClusterManager clusterManager;
    private RedissonClient         redissonClient = RedissonHelper.newRedissonClient();
    private RedissonHaManager      haManager;
    private ClusterRuleEngine      ruleEngine;

    private Rule rule;

    @SneakyThrows
    public void initRuleModel() {
        ClassPathResource resource = new ClassPathResource("test.re.xml");

        XmlRuleModelParserStrategy strategy = new XmlRuleModelParserStrategy();
        RuleModel model = strategy.parse(StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8));
        rule = new Rule();
        rule.setId("test-1.0");
        rule.setVersion(1);
        rule.setModel(model);
    }

    @Before
    public void setup() {
        initRuleModel();

        NodeInfo nodeInfo = new NodeInfo();
        nodeInfo.setId("test-node");
        nodeInfo.setUptime(System.currentTimeMillis());
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(5);

        haManager = new RedissonHaManager();
        haManager.setCurrentNode(nodeInfo);
        haManager.setExecutorService(executorService);
        haManager.setRedissonClient(redissonClient);
        haManager.setClusterName("cluster-"+IDGenerator.UUID.generate());

        clusterManager = new RedissonClusterManager();
        clusterManager.setName("cluster-"+IDGenerator.UUID.generate());
        clusterManager.setRedissonClient(redissonClient);
        clusterManager.setHaManager(haManager);
        clusterManager.setExecutorService(executorService);
        clusterManager.start();
        haManager.start();

        ruleEngine = new ClusterRuleEngine();

        ruleEngine.setClusterManager(clusterManager);
        ruleEngine.setNodeSelector((model, allNode) -> allNode);
        ruleEngine.setInstanceRepository(new MockRuleInstanceRepository());
        ruleEngine.setModelParser((format, modelDefineString) -> rule.getModel());
        ruleEngine.setRuleRepository(ruleId -> {
            RulePersistent persistent = new RulePersistent();
            persistent.setRuleId(ruleId);
            persistent.setId(IDGenerator.MD5.generate());
            return Optional.of(persistent);
        });
        ruleEngine.start();

        DefaultExecutableRuleNodeFactory nodeFactory = new DefaultExecutableRuleNodeFactory();
        nodeFactory.registerStrategy(new JavaMethodInvokeStrategy());

        RuleEngineWorker worker = new RuleEngineWorker();
        worker.setRuleRepository(ruleEngine.getRuleRepository());
        worker.setModelParser(ruleEngine.getModelParser());

        worker.setClusterManager(clusterManager);
        worker.setConditionEvaluator((condition, data) -> true);
        worker.setNodeFactory(nodeFactory);
        worker.start();
    }

    @After
    public void after() {
        clusterManager.shutdown();
        haManager.shutdown();
    }

    @Test
    @SneakyThrows
    public void testClusterRule() {
        rule.getModel().setRunMode(RunMode.CLUSTER);
        //start
        RuleInstanceContext context = ruleEngine.startRule(rule);
        //test get running instance
        context = ruleEngine.getInstance(context.getId());
        Assert.assertNotNull(context);
        //mock restart server
        ruleEngine.contextCache.clear();
        //
        context = ruleEngine.getInstance(context.getId());
        Assert.assertNotNull(context);

        for (int i = 0; i < 100; i++) {
            RuleData ruleData = context.execute(RuleData.create("abc1234"))
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            Assert.assertEquals(ruleData.getData(), "abc1234_");
            System.out.println(ruleData.getData());
        }
        RuleData ruleData = context.execute(newHelper(RuleData.create("abc1234"))
                .markEndWith("append-underline")
                .done())
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        Assert.assertNotNull(ruleData);
        Assert.assertEquals(ruleData.getData(), "ABC1234_");
        RuleData ruleData2 = context.execute(newHelper(RuleData.create("ABC1234"))
                .markStartWith("to-low-case")
                .markEndWith("event-done")
                .done())
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        Assert.assertNotNull(ruleData2);
        Assert.assertEquals(ruleData2.getData(), "abc1234");

        context.stop();
        Thread.sleep(1000);
    }

    @Test
    @SneakyThrows
    public void testDistributedRule() {
        rule.getModel().setRunMode(RunMode.DISTRIBUTED);
        //start
        RuleInstanceContext context = ruleEngine.startRule(rule);
        //test get running instance
        context = ruleEngine.getInstance(context.getId());
        Assert.assertNotNull(context);
        //mock restart server
        ruleEngine.contextCache.clear();
        //
        context = ruleEngine.getInstance(context.getId());
        Assert.assertNotNull(context);
        for (int i = 0; i < 100; i++) {
            RuleData ruleData = context.execute(RuleData.create("abc1234"))
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            Assert.assertEquals(ruleData.getData(), "abc1234_");
            System.out.println(ruleData.getData());
        }
        RuleData ruleData = context.execute(newHelper(RuleData.create("abc1234"))
                .markEndWith("append-underline")
                .done())
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        Assert.assertNotNull(ruleData);
        Assert.assertEquals(ruleData.getData(), "ABC1234_");
        RuleData ruleData2 = context.execute(newHelper(RuleData.create("ABC1234"))
                .markStartWith("to-low-case")
                .markEndWith("event-done")
                .done())
                .toCompletableFuture()
                .get(10, TimeUnit.SECONDS);
        Assert.assertNotNull(ruleData2);
        Assert.assertEquals(ruleData2.getData(), "abc1234");

        context.stop();
        Thread.sleep(1000);
    }
}