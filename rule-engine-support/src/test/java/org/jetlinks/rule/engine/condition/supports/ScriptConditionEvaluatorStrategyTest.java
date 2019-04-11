package org.jetlinks.rule.engine.condition.supports;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class ScriptConditionEvaluatorStrategyTest {
    @Test
    public void testjs() {
        ScriptConditionEvaluatorStrategy strategy = new ScriptConditionEvaluatorStrategy(new DefaultScriptEvaluator());

        Condition condition = new Condition();
        condition.setConfiguration(new HashMap<>());
        condition.setType("script");
        condition.getConfiguration().put("lang", "js");
        condition.getConfiguration().put("script", "return data.val>=60;");

        boolean gte60 = strategy.evaluate(condition, RuleData.create(Collections.singletonMap("val", 60)));

        Assert.assertTrue(gte60);

        gte60 = strategy.evaluate(condition, RuleData.create(Collections.singletonMap("val", 50)));

        Assert.assertFalse(gte60);


    }
}