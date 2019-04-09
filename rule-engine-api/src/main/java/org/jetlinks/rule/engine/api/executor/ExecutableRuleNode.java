package org.jetlinks.rule.engine.api.executor;

import java.io.Closeable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutableRuleNode {
    Closeable start(ExecutionContext executionContext);
}
