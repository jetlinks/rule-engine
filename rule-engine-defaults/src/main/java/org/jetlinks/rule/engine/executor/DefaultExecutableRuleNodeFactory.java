package org.jetlinks.rule.engine.executor;

import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.executor.StreamRuleNode;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
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

    @Override
    public StreamRuleNode createStream(RuleNodeConfiguration configuration) {

        return null;
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
            executableRuleNode = Optional.ofNullable(strategySupports.get(configuration.getExecutor()))
                    .map(strategy -> strategy.create(configuration))
                    .orElseThrow(() -> new UnsupportedOperationException("不支持的节点类型:" + configuration.getExecutor()));
            configHash = configuration.hashCode();
        }
    }

    public void registerStrategy(ExecutableRuleNodeFactoryStrategy strategy) {
        strategySupports.put(strategy.getSupportType(), strategy);
    }
}
