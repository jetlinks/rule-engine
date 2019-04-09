package org.jetlinks.rule.engine.api.executor;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutableRuleNodeFactory {
    ExecutableRuleNode create(RuleNodeConfiguration configuration);
}
