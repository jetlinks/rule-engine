package org.jetlinks.rule.engine.api.task;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 当前任务生命周期内的执行指标，只读快照，不用于持久化或任务恢复。
 * 次数在有限执行订阅时累计，空结果也计数；取消不计失败，时间为最近一次执行开始时间。
 *
 * @since 1.3.3
 * @see TaskSnapshot
 * @see ExecutionContext#observeExecution
 */
@Getter
@Setter
public class TaskExecutionMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    private long executionCount;
    private long errorCount;
    private long lastExecutionTime;
}
