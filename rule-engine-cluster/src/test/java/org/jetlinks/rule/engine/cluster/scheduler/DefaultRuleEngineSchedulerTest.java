package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.SneakyThrows;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.model.*;
import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.persistent.repository.RuleRepository;
import org.jetlinks.rule.engine.cluster.TestExecutor;
import org.jetlinks.rule.engine.cluster.redisson.RedissonClusterManager;
import org.jetlinks.rule.engine.cluster.redisson.RedissonHaManager;
import org.jetlinks.rule.engine.cluster.redisson.RedissonHelper;
import org.jetlinks.rule.engine.cluster.repository.MockRuleInstanceRepository;
import org.jetlinks.rule.engine.cluster.worker.RuleEngineWorker;
import org.jetlinks.rule.engine.condition.DefaultConditionEvaluator;
import org.jetlinks.rule.engine.condition.supports.DefaultScriptEvaluator;
import org.jetlinks.rule.engine.condition.supports.ScriptConditionEvaluatorStrategy;
import org.jetlinks.rule.engine.executor.DefaultExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.executor.supports.JavaMethodInvokeStrategy;
import org.jetlinks.rule.engine.model.DefaultRuleModelParser;
import org.jetlinks.rule.engine.model.xml.XmlRuleModelParserStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.jetlinks.rule.engine.api.RuleDataHelper.newHelper;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultRuleEngineSchedulerTest {
    private RedissonClusterManager clusterManager;
    private RedissonClient         redissonClient = RedissonHelper.newRedissonClient();
    private RedissonHaManager      haManager;
    private DefaultRuleEngineScheduler ruleEngine;

    private DefaultRuleModelParser modelParser;
    private Rule                   rule;
    private String                 modelString;

    @SneakyThrows
    public void initRuleModel() {
        ClassPathResource resource = new ClassPathResource("test.re.xml");
        XmlRuleModelParserStrategy strategy = new XmlRuleModelParserStrategy();
        modelParser = new DefaultRuleModelParser();
        modelParser.register(strategy);
        modelString = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);
        RuleModel model = strategy.parse(modelString);
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
        ScheduledExecutorService executorService = Executors.newScheduledThreadPool(10);

        haManager = new RedissonHaManager();
        haManager.setCurrentNode(nodeInfo);
        haManager.setExecutorService(executorService);
        haManager.setRedissonClient(redissonClient);
        haManager.setClusterName("cluster-" + IDGenerator.UUID.generate());

        clusterManager = new RedissonClusterManager();
        clusterManager.setName(haManager.getClusterName());
        clusterManager.setRedissonClient(redissonClient);
        clusterManager.setHaManager(haManager);
        clusterManager.setExecutorService(executorService);
        clusterManager.start();
        haManager.start();

        ruleEngine = new DefaultRuleEngineScheduler();

        ruleEngine.setClusterManager(clusterManager);
        ruleEngine.setNodeSelector((model, allNode) -> allNode);
        ruleEngine.setInstanceRepository(new MockRuleInstanceRepository());
        ruleEngine.setModelParser(new RuleEngineModelParser() {
            @Override
            public RuleModel parse(String format, String modelDefineString) {
                return rule.getModel();
            }

            @Override
            public List<String> getAllSupportFormat() {
                return Collections.emptyList();
            }
        });
        ruleEngine.setRuleRepository(new RuleRepository() {
            @Override
            public Optional<RulePersistent> findRuleById(String ruleId) {
                RulePersistent persistent = new RulePersistent();
                persistent.setRuleId(ruleId);
                persistent.setModelFormat("re.xml");
                persistent.setModel(modelString);
                persistent.setId(IDGenerator.MD5.generate());
                return Optional.of(persistent);
            }

            @Override
            public List<RulePersistent> findRuleByIdList(Collection<String> ruleIdList) {
                return findRuleById("")
                        .map(Collections::singletonList)
                        .orElse(Collections.emptyList());
            }

            @Override
            public void save(RulePersistent persistent) {

            }
        });
        ruleEngine.start();

        DefaultExecutableRuleNodeFactory nodeFactory = new DefaultExecutableRuleNodeFactory();
        nodeFactory.registerStrategy(new JavaMethodInvokeStrategy());

        DefaultConditionEvaluator evaluator = new DefaultConditionEvaluator();
        evaluator.register(new ScriptConditionEvaluatorStrategy(new DefaultScriptEvaluator()));

        RuleEngineWorker worker = new RuleEngineWorker();
        worker.setModelParser(ruleEngine.getModelParser());

        worker.setClusterManager(clusterManager);

        worker.setConditionEvaluator(evaluator);
        worker.setNodeFactory(nodeFactory);
        worker.start();
    }

    @After
    public void after() {
        clusterManager.shutdown();
        haManager.shutdown();
        TestExecutor.counter.set(0);
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
        Assert.assertEquals(100, TestExecutor.counter.intValue());
        TestExecutor.counter.set(0);

        context.execute(consumer -> {
            for (int i = 0; i < 100; i++) {
                consumer.apply(RuleData.create("aaaa" + i));
            }
        });
        Thread.sleep(2000);
        Assert.assertEquals(100, TestExecutor.counter.intValue());
        TestExecutor.counter.set(0);

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
        Assert.assertEquals(100, TestExecutor.counter.intValue());
        TestExecutor.counter.set(0);

        context.execute(consumer -> {
            for (int i = 0; i < 10; i++) {
                consumer.apply(RuleData.create("aaaa" + i));
            }
        });
        Thread.sleep(2000);
        Assert.assertEquals(10, TestExecutor.counter.intValue());
        TestExecutor.counter.set(0);

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