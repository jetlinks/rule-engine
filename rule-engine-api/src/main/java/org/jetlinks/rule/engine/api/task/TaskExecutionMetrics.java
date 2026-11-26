package org.jetlinks.rule.engine.api.task;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Setter;
import org.jetlinks.core.annotation.metric.Counter;
import org.jetlinks.core.annotation.metric.EwmaRate;
import org.jetlinks.core.annotation.metric.Rate;
import org.jetlinks.core.annotation.metric.Timestamp;

import java.io.Serializable;

/**
 * 当前任务生命周期内的执行指标，只读快照，不用于持久化或任务恢复。
 * 次数在有限执行订阅时累计，空结果也计数；取消不计失败，时间为最近一次执行开始时间。
 * 速率按执行开始计量，快照各字段为弱一致读取，不表示动作完成吞吐。
 *
 * @since 1.3.3
 * @see TaskSnapshot
 * @see ExecutionContext#observeExecution
 */
@Setter
public class TaskExecutionMetrics implements Serializable {
    private static final long serialVersionUID = 1L;

    private long executionCount;
    private long errorCount;
    private long lastExecutionTime;
    private double meanExecutionRate;
    private double oneMinuteExecutionRate;

    @Counter
    @Schema(title = "执行次数", description = "当前任务实例的有限执行开始次数，空结果和取消也计数")
    public long getExecutionCount() {
        return executionCount;
    }

    @Counter
    @Schema(title = "错误执行次数", description = "出现过错误的执行次数，包含随后恢复的错误；同次执行只计一次")
    public long getErrorCount() {
        return errorCount;
    }

    @Timestamp
    @Schema(title = "最近执行时间", description = "最近一次执行开始的epoch毫秒时间，尚未执行时为0")
    public long getLastExecutionTime() {
        return lastExecutionTime;
    }

    @Rate(unit = "count/s")
    @Schema(title = "平均执行速率", description = "当前任务实例创建以来的平均执行开始次数/秒")
    public double getMeanExecutionRate() {
        return meanExecutionRate;
    }

    @EwmaRate(value = 60, tickSeconds = 5, unit = "count/s")
    @Schema(title = "1分钟平滑执行速率", description = "时间常数60秒的执行开始次数/秒，5秒惰性推进，首次节拍前为0；不是精确窗口平均")
    public double getOneMinuteExecutionRate() {
        return oneMinuteExecutionRate;
    }
}
