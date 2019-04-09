package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.SneakyThrows;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.model.*;
import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.cluster.NodeInfo;
import org.jetlinks.rule.engine.cluster.redisson.RedissonClusterManager;
import org.jetlinks.rule.engine.cluster.redisson.RedissonHaManager;
import org.jetlinks.rule.engine.cluster.redisson.RedissonHelper;
import org.jetlinks.rule.engine.cluster.repository.MockRuleInstanceRepository;
import org.jetlinks.rule.engine.cluster.worker.RuleEngineWorker;
import org.jetlinks.rule.engine.executor.DefaultExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.executor.supports.JavaMethodInvokeStrategy;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.redisson.api.RedissonClient;

import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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

    public void initRuleModel() {
        RuleModel model = new RuleModel();
        model.setId("test");
        model.setName("测试");
        model.setRunMode(RunMode.CLUSTER);
        RuleNodeModel startNode = new RuleNodeModel();
        startNode.setId("start");
        startNode.setExecutor("java-method");
        startNode.setName("执行java方法");
        startNode.setNodeType(NodeType.MAP);
        startNode.addConfiguration("className", "org.jetlinks.rule.engine.cluster.TestExecutor");
        startNode.addConfiguration("methodName", "execute");
        startNode.setStart(true);

        RuleNodeModel end = new RuleNodeModel();
        end.setId("end");
        end.setExecutor("java-method");
        end.setName("执行java方法");
        end.setNodeType(NodeType.PEEK);
        end.addConfiguration("className", "org.jetlinks.rule.engine.cluster.TestExecutor");
        end.addConfiguration("methodName", "execute2");
        end.setEnd(true);
        RuleNodeModel log = new RuleNodeModel();
        log.setId("log");
        log.setExecutor("java-method");
        log.setName("执行java方法");
        log.setNodeType(NodeType.PEEK);
        log.addConfiguration("className", "org.jetlinks.rule.engine.cluster.TestExecutor");
        log.addConfiguration("methodName", "execute3");

        RuleNodeModel errorEvent = new RuleNodeModel();
        errorEvent.setId("error-event");
        errorEvent.setExecutor("java-method");
        errorEvent.setName("执行java方法");
        errorEvent.setNodeType(NodeType.PEEK);
        errorEvent.addConfiguration("className", "org.jetlinks.rule.engine.cluster.TestExecutor");
        errorEvent.addConfiguration("methodName", "event1");

        RuleLink event1 = new RuleLink();
        event1.setTarget(errorEvent);
        event1.setSource(log);
        event1.setType(RuleEvent.NODE_EXECUTE_FAIL);
        event1.setId("error-event-link");

        errorEvent.getInputs().add(event1);

        log.getEvents().add(event1);


        RuleLink link = new RuleLink();
        link.setCondition(null);
        link.setId("link-start-end");
        link.setTarget(end);
        link.setSource(startNode);

        RuleLink logLink = new RuleLink();
        logLink.setCondition(null);
        logLink.setId("link-start-log");
        logLink.setTarget(log);
        logLink.setSource(startNode);

        log.getInputs().add(logLink);
        end.getInputs().add(link);

        startNode.getOutputs().add(link);
        startNode.getOutputs().add(logLink);


        rule = new Rule();
        rule.setId("test-1.0");
        rule.setVersion(1);
        rule.setModel(model);
        startNode.setRuleId(rule.getId());
        end.setRuleId(rule.getId());
        log.setRuleId(rule.getId());
        errorEvent.setRuleId(rule.getId());

        model.getNodes().add(startNode);
        model.getNodes().add(end);
        model.getNodes().add(log);
        model.getNodes().add(errorEvent);
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
            Object data = context
                    .execute(RuleData.create("abc123"))
                    .toCompletableFuture()
                    .get(20, TimeUnit.SECONDS)
                    .getData();
            Assert.assertEquals(data, "ABC123");
        }
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
            Object data = context
                    .execute(RuleData.create("abc123"))
                    .toCompletableFuture()
                    .get(20, TimeUnit.SECONDS)
                    .getData();
            Assert.assertEquals(data, "ABC123");
        }
        context.stop();
        Thread.sleep(1000);
    }
}