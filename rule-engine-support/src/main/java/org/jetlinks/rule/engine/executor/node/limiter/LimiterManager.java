package org.jetlinks.rule.engine.executor.node.limiter;

public interface LimiterManager {
    Limiter getLimiter(String key);
}
