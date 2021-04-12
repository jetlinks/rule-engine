package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.RuleData;
import reactor.core.publisher.Mono;

/**
 * 可直接执行的任务执行器
 *
 * @author zhouhao
 * @since 1.1.6
 */
public interface ExecutableTaskExecutor extends TaskExecutor {

    /**
     * 执行任务
     *
     * @param ruleData 规则数据
     * @return void
     */
    Mono<Void> execute(RuleData ruleData);

}
