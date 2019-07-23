package org.jetlinks.rule.engine.executor;

import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.executor.supports.RuleNodeConfig;

public interface GenericConfigExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig> extends ExecutableRuleNodeFactoryStrategy {

    Class<C> getConfigType();

    C newConfigInstance(RuleNodeConfiguration configuration);

}
