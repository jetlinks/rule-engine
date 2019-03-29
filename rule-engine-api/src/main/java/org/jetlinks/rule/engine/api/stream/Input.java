package org.jetlinks.rule.engine.api.stream;

import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface Input {
    void accept(Consumer<StreamData> accept);

    boolean acceptOnce(Consumer<StreamData> accept);
}
