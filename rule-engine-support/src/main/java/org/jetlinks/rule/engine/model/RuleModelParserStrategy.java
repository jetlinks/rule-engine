package org.jetlinks.rule.engine.model;

import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.api.model.RuleModel;

/**
 * 模型解析器策略
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleModelParserStrategy {

    String getFormat();

    RuleModel parse(String modelDefineString);
}
