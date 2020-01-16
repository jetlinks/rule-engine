package org.jetlinks.rule.engine.executor;

import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutableRuleNodeFactoryStrategy {
    String getSupportType();

    ExecutableRuleNode create(RuleNodeConfiguration configuration);

    default ExecutableRuleNodeDescription getDescription() {
        return null;
    }
}
