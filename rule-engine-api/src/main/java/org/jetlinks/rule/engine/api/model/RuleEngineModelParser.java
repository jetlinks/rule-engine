package org.jetlinks.rule.engine.api.model;

import java.util.List;

/**
 * 规则引擎模型解析器，支持将不同的模型格式转换为规则模型
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngineModelParser {

    /**
     * 解析指定格式的模型字符为规则模型
     *
     * @param format            模型格式
     * @param modelDefineString 字符模型
     * @return 规则模型
     */
    RuleModel parse(String format, String modelDefineString);

    /**
     * @return 全部支持的模型格式
     */
    List<String> getAllSupportFormat();
}
