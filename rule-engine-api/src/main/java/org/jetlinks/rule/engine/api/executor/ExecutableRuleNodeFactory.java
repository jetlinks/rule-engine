package org.jetlinks.rule.engine.api.executor;

import java.util.List;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutableRuleNodeFactory {
    ExecutableRuleNode create(RuleNodeConfiguration configuration);

    List<String> getAllSupportExecutor();
}
