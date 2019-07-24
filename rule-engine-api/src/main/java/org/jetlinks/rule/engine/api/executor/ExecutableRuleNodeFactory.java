package org.jetlinks.rule.engine.api.executor;

import java.util.List;

/**
 * 可执行规则节点工厂,用于将规则节点配置转换为可执行节点
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutableRuleNodeFactory {

    /**
     * 创建可执行规则
     *
     * @param configuration 规则节点配置信息
     * @return 可执行规则
     */
    ExecutableRuleNode create(RuleNodeConfiguration configuration);

    /**
     * @return 所有支持的规则执行器名称
     */
    List<String> getAllSupportExecutor();
}
