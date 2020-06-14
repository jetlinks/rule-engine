package org.jetlinks.rule.engine.api.task;

import reactor.core.publisher.Mono;

public interface TaskExecutorProvider {

    String getExecutor();

    Mono<TaskExecutor> createTask(ExecutionContext context);

}
