package org.jetlinks.rule.engine.api.model;

/**
 * 规则引起模型解析器
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngineModelParser {

    RuleModel parse(String format, String modelDefineString);

}
