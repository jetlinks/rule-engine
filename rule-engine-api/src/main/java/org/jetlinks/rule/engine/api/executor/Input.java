package org.jetlinks.rule.engine.api.executor;

import org.jetlinks.rule.engine.api.RuleData;

import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface Input {

    boolean accept(Consumer<RuleData> accept);

    void close();
}
