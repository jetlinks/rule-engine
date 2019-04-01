package org.jetlinks.rule.engine.api.executor;


import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutableRuleNode {
    CompletionStage<Object> execute(ExecutionContext context);
}
