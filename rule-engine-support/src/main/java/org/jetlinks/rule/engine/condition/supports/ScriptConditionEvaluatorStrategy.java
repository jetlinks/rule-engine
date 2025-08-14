package org.jetlinks.rule.engine.condition.supports;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.Condition;
import org.jetlinks.rule.engine.condition.ConditionEvaluatorStrategy;

import java.util.HashMap;
import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Deprecated
public class ScriptConditionEvaluatorStrategy implements ConditionEvaluatorStrategy {

    private final ScriptEvaluator scriptEvaluator;

    public ScriptConditionEvaluatorStrategy(ScriptEvaluator scriptEvaluator) {
        this.scriptEvaluator = scriptEvaluator;
    }

    @Override
    public String getType() {
        return "script";
    }

    @Override
    @SneakyThrows
    public boolean evaluate(Condition condition, RuleData context) {
        String lang = condition.<String>getConfig("lang")
                .orElseThrow(() -> new IllegalArgumentException("配置:lang不能为空"));
        String script = condition.<String>getConfig("script")
                .orElseThrow(() -> new IllegalArgumentException("配置:script不能为空"));
        Map<String, Object> scriptContext = new HashMap<>();
        scriptContext.put("ruleData", context);
        scriptContext.put("attr", context.getHeaders());
        scriptContext.put("data", context.getData());
        Object val = scriptEvaluator.evaluate(lang, script, scriptContext);

        return Boolean.TRUE.equals(val) || "true".equals(val);
    }
}
