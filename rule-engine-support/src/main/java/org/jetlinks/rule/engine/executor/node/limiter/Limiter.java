package org.jetlinks.rule.engine.executor.node.limiter;

import reactor.core.publisher.Mono;

public interface Limiter {

    Mono<Boolean> tryLimit(long time,long limit);

}
