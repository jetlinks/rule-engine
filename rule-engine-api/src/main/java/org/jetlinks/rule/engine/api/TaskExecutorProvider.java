package org.jetlinks.rule.engine.api;

import reactor.core.publisher.Mono;

public interface TaskExecutorProvider {

    String getExecutor();

    Mono<TaskExecutor> createTask(ExecutionContext context);

}
