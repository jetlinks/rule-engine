package org.jetlinks.rule.engine.api.model;

import java.util.List;

/**
 * 规则引擎模型解析器
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngineModelParser {

    RuleModel parse(String format, String modelDefineString);

    List<String> getAllSupportFormat();
}
