package org.jetlinks.rule.engine.executor;

import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultExecutableRuleNodeFactory implements ExecutableRuleNodeFactory {

    private Map<String, ExecutableRuleNodeFactoryStrategy> strategySupports = new HashMap<>();

    private Map<String, Cache> cache = new ConcurrentHashMap<>();

    @Override
    public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
        return cache.computeIfAbsent(configuration.getId(), id -> new Cache())
                .tryReload(configuration);
    }

    private class Cache {
        private long configHash;

        private volatile ExecutableRuleNode executableRuleNode;

        private ExecutableRuleNode tryReload(RuleNodeConfiguration configuration) {
            if (configuration.hashCode() != configHash) {
                doReload(configuration);
            }
            return executableRuleNode;
        }

        private void doReload(RuleNodeConfiguration configuration) {
            executableRuleNode = strategySupports.get(configuration.getType())
                    .create(configuration);
            configHash = configuration.hashCode();
        }
    }

    public void registerStrategy(ExecutableRuleNodeFactoryStrategy strategy) {
        strategySupports.put(strategy.getSupportType(), strategy);
    }
}
