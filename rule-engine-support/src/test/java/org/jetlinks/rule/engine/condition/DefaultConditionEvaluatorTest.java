package org.jetlinks.rule.engine.condition;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultConditionEvaluatorTest {

    @Test
    public void test() {
        DefaultConditionEvaluator evaluator = new DefaultConditionEvaluator();

        evaluator.register(new ConditionEvaluatorStrategy() {
            @Override
            public String getType() {
                return "test";
            }

            @Override
            public boolean evaluate(Condition condition, RuleData data) {
                return condition.getConfig("test").isPresent();
            }
        });

        Condition condition = new Condition();
        condition.setType("test");
        condition.setConfiguration(Collections.singletonMap("test", 1234));

        Assert.assertTrue(evaluator.evaluate(condition, RuleData.create("test")));

    }
}