package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.model.RuleModel;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngine {

    RunningRuleContext startRule(RuleModel model);

    RunningRuleContext getRunningRule(String id);
    
}
