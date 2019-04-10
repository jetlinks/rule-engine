package org.jetlinks.rule.engine.model;

import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.api.model.RuleModel;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultRuleModelParser implements RuleEngineModelParser {

    private Map<String, RuleModelParserStrategy> allStrategy = new HashMap<>();


    @Override
    public RuleModel parse(String format, String modelDefineString) {
        return Optional.ofNullable(allStrategy.get(format))
                .map(strategy -> strategy.parse(modelDefineString))
                .orElseThrow(() -> new UnsupportedOperationException("不支持的格式:" + format));
    }

    public void register(RuleModelParserStrategy strategy) {
        allStrategy.put(strategy.getFormat(), strategy);
    }
}
