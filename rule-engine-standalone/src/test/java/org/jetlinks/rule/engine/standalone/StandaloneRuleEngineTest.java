package org.jetlinks.rule.engine.standalone;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.ConditionEvaluator;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.executor.DefaultExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.executor.supports.JavaMethodInvokeStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class StandaloneRuleEngineTest {

    private StandaloneRuleEngine engine;

    @Before
    public void init() {
        DefaultExecutableRuleNodeFactory nodeFactory = new DefaultExecutableRuleNodeFactory();
        nodeFactory.registerStrategy(new JavaMethodInvokeStrategy());
        ConditionEvaluator evaluator = (condition, context) -> true;


        engine = new StandaloneRuleEngine();
        engine.setEvaluator(evaluator);
        engine.setNodeFactory(nodeFactory);

    }


    @Test
    @SneakyThrows
    public void testRuleEngine() {
        RuleModel model = new RuleModel();
        model.setId("test");
        model.setName("测试");

        RuleNodeModel startNode = new RuleNodeModel();
        startNode.setId("start");
        startNode.setExecutor("java-method");
        startNode.setName("执行java方法");
        startNode.setNodeType(NodeType.MAP);
        startNode.setStart(true);
        startNode.addConfiguration("className", "org.jetlinks.rule.engine.standalone.TestExecutor");
        startNode.addConfiguration("methodName", "execute");

        RuleNodeModel end = new RuleNodeModel();
        end.setId("end");
        end.setExecutor("java-method");
        end.setName("执行java方法");
        end.setEnd(true);
        end.setNodeType(NodeType.PEEK);
        end.addConfiguration("className", "org.jetlinks.rule.engine.standalone.TestExecutor");
        end.addConfiguration("methodName", "execute2");

        RuleNodeModel log = new RuleNodeModel();
        log.setId("log");
        log.setExecutor("java-method");
        log.setName("执行java方法");
        log.setNodeType(NodeType.PEEK);
        log.addConfiguration("className", "org.jetlinks.rule.engine.standalone.TestExecutor");
        log.addConfiguration("methodName", "execute3");

        RuleNodeModel afterEvent = new RuleNodeModel();
        afterEvent.setId("after-event");
        afterEvent.setExecutor("java-method");
        afterEvent.setName("执行java方法");
        afterEvent.setNodeType(NodeType.PEEK);
        afterEvent.addConfiguration("className", "org.jetlinks.rule.engine.standalone.TestExecutor");
        afterEvent.addConfiguration("methodName", "event1");

        RuleLink event1 = new RuleLink();
        event1.setTarget(afterEvent);
        event1.setSource(log);
        event1.setType(RuleEvent.NODE_EXECUTE_FAIL);
        event1.setId("after-event-link");

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


        startNode.getOutputs().add(link);
        startNode.getOutputs().add(logLink);

        model.getNodes().add(startNode);
        model.getNodes().add(end);
        model.getNodes().add(afterEvent);
        model.getNodes().add(log);

        Rule rule = new Rule();
        rule.setId("test:1.0");
        rule.setVersion(1);
        rule.setModel(model);

        RuleInstanceContext context = engine.startRule(rule);
        Assert.assertNotNull(context);
        Assert.assertNotNull(context.getId());
        for (int i = 0; i < 100; i++) {
            RuleData ruleData = context.execute(RuleData.create("abc1234"))
                    .toCompletableFuture()
                    .get(10, TimeUnit.SECONDS);

            Assert.assertEquals(ruleData.getData(), "ABC1234");
            System.out.println(ruleData.getData());
        }


    }

}