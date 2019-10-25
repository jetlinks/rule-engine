package org.jetlinks.rule.engine.standalone;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.model.RuleModel;
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
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

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

        RuleInstanceContext context = engine.startRule(rule).block();
        Assert.assertNotNull(context);
        Assert.assertNotNull(context.getId());

        Flux.range(0, 5)
                .map(i -> RuleData.create("abc" + i))
                .as(context::execute)
                .as(StepVerifier::create)
                .expectNextCount(5)
                .verifyComplete();

        context.stop();
    }

}