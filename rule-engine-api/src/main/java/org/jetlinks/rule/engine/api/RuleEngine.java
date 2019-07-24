package org.jetlinks.rule.engine.api;

/**
 * 规则引擎
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngine {

    /**
     * 启动规则
     *
     * @param model 规则模型
     * @return 规则实例上下文
     */
    RuleInstanceContext startRule(Rule model);

    /**
     * 获取运行中的规则实例
     *
     * @param id 实例ID
     * @return 规则实例上下文
     */
    RuleInstanceContext getInstance(String id);

}
