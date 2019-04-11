package org.jetlinks.rule.engine.standalone;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.condition.DefaultConditionEvaluator;
import org.jetlinks.rule.engine.condition.supports.DefaultScriptEvaluator;
import org.jetlinks.rule.engine.condition.supports.ScriptConditionEvaluatorStrategy;
import org.jetlinks.rule.engine.executor.DefaultExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.executor.supports.JavaMethodInvokeStrategy;
import org.jetlinks.rule.engine.model.xml.XmlRuleModelParserStrategy;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.jetlinks.rule.engine.api.RuleDataHelper.*;

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
        DefaultConditionEvaluator evaluator = new DefaultConditionEvaluator();
        evaluator.register(new ScriptConditionEvaluatorStrategy(new DefaultScriptEvaluator()));

        engine = new StandaloneRuleEngine();
        engine.setEvaluator(evaluator);
        engine.setNodeFactory(nodeFactory);

    }


    @Test
    @SneakyThrows
    public void testRuleEngine() {
        ClassPathResource resource = new ClassPathResource("test.re.xml");

        XmlRuleModelParserStrategy strategy = new XmlRuleModelParserStrategy();
        RuleModel model = strategy.parse(StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8));

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

    }

}