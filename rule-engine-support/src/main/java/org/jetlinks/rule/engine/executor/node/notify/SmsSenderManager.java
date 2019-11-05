package org.jetlinks.rule.engine.executor.node.notify;

import reactor.core.publisher.Mono;

public interface SmsSenderManager  {

    Mono<SmsSender> getSender(String id);
}
