package org.jetlinks.rule.engine.api.stream;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface Output {
    void write(StreamData data);
}
