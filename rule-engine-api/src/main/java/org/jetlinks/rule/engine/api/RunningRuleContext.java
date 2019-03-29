package org.jetlinks.rule.engine.api;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RunningRuleContext {
    String getId();

    long getStartTime();

    void putData(Object data);

    void stop();
}
