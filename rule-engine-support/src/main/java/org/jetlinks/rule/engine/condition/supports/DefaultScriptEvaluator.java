package org.jetlinks.rule.engine.condition.supports;

import org.apache.commons.codec.digest.DigestUtils;
import org.hswebframework.expands.script.engine.DynamicScriptEngine;
import org.hswebframework.expands.script.engine.DynamicScriptEngineFactory;

import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultScriptEvaluator implements ScriptEvaluator {
    @Override
    public Object evaluate(String lang, String script, Map<String, Object> context) throws Exception {
        DynamicScriptEngine engine = DynamicScriptEngineFactory.getEngine(lang);
        if (engine == null) {
            throw new UnsupportedOperationException("不支持的脚本语言:" + lang);
        }
        String scriptId = DigestUtils.md5Hex(script);
        if (!engine.compiled(scriptId)) {
            engine.compile(scriptId, script);
        }

        return engine.execute(scriptId, context).getIfSuccess();
    }
}
