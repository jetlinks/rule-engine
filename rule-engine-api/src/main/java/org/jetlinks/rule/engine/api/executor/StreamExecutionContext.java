package org.jetlinks.rule.engine.api.executor;


import org.jetlinks.rule.engine.api.stream.Input;
import org.jetlinks.rule.engine.api.stream.Output;
import org.jetlinks.rule.engine.api.RuleData;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface StreamExecutionContext extends ExecutionContext {
    Input getInput();

    Output getOutput();

    void onError(RuleData data, Throwable e);
}
