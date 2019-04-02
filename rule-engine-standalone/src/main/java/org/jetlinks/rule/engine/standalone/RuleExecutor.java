package org.jetlinks.rule.engine.standalone;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.NodeType;

import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleExecutor {

    NodeType getNodeType();

    CompletionStage<RuleData> execute(RuleData ruleData);

    void addNext(RuleExecutor executor);

    boolean should(RuleData data);

    void addEventListener(String event, RuleExecutor executor);
}
