package org.jetlinks.rule.engine.api;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEngine {

    RuleInstanceContext startRule(Rule model);

    RuleInstanceContext getInstance(String id);
    
}
