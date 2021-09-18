package org.jetlinks.rule.engine.model;

import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.api.model.RuleModel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultRuleModelParser implements RuleEngineModelParser {

    private final Map<String, RuleModelParserStrategy> allStrategy = new ConcurrentHashMap<>();

    @Override
    public List<String> getAllSupportFormat() {
        return new ArrayList<>(allStrategy.keySet());
    }

    @Override
    public RuleModel parse(String format, String modelDefineString) {
        return Optional
                .ofNullable(allStrategy.get(format))
                .map(strategy -> {
                    RuleModel model = strategy.parse(modelDefineString);
                    model.setType(format);
                    return model;
                })
                .orElseThrow(() -> new UnsupportedOperationException("不支持的模型格式:" + format));
    }

    public void register(RuleModelParserStrategy strategy) {
        allStrategy.put(strategy.getFormat(), strategy);
    }
}
