package org.jetlinks.rule.engine.standalone;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.model.NodeType;

import java.util.concurrent.CompletionStage;
import java.util.function.Predicate;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleExecutor {

    NodeType getNodeType();

    CompletionStage<RuleData> execute(RuleData ruleData);

    void addNext(Predicate<RuleData> condition, RuleExecutor executor);

    void addEventListener(String event, RuleExecutor executor);

    void addEventListener(GlobalNodeEventListener listener);

    void start();

    void stop();
}
