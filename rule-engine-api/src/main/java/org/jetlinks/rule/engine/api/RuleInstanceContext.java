package org.jetlinks.rule.engine.api;

import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleInstanceContext {
    String getId();

    long getStartTime();

    CompletionStage<RuleData> execute(RuleData data);

    void execute(Consumer<Consumer<RuleData>> dataSink);

    void stop();

}
