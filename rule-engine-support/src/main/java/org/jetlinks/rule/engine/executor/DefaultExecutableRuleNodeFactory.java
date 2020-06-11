package org.jetlinks.rule.engine.executor;

import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;

import java.util.*;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Deprecated
public class DefaultExecutableRuleNodeFactory implements ExecutableRuleNodeFactory {

    private Map<String, ExecutableRuleNodeFactoryStrategy> strategySupports = new HashMap<>();

    @Override
    public List<String> getAllSupportExecutor() {
        return new ArrayList<>(strategySupports.keySet());
    }

    @Override
    public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
        return Optional.ofNullable(strategySupports.get(configuration.getExecutor()))
                .map(strategy -> strategy.create(configuration))
                .orElseThrow(() -> new UnsupportedOperationException("不支持的节点类型:" + configuration.getExecutor()));
    }

    public void registerStrategy(ExecutableRuleNodeFactoryStrategy strategy) {
        strategySupports.put(strategy.getSupportType(), strategy);
    }
}
