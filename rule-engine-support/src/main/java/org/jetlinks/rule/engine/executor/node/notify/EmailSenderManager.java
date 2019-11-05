package org.jetlinks.rule.engine.executor.node.notify;

import reactor.core.publisher.Mono;

public interface EmailSenderManager {

    Mono<EmailSender> getSender(String id);

}
