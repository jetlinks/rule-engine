package org.jetlinks.rule.engine.api;

import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleInstanceContext {
    String getId();

    long getStartTime();

    CompletionStage<RuleData> execute(RuleData data);

    void stop();

}
