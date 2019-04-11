package org.jetlinks.rule.engine.condition.supports;

import lombok.SneakyThrows;
import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.*;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultScriptEvaluatorTest {

    @Test
    @SneakyThrows
    public void testJs() {
        DefaultScriptEvaluator evaluator = new DefaultScriptEvaluator();

        Assert.assertEquals(evaluator.evaluate("js", "return 1234;", Collections.emptyMap()), 1234);

    }
}