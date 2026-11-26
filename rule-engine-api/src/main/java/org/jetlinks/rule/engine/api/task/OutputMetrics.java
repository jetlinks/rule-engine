package org.jetlinks.rule.engine.api.task;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jetlinks.core.annotation.metric.Counter;
import org.jetlinks.core.annotation.metric.EwmaRate;

import java.io.Serializable;

/** Output 实例生命周期内的只读数据发出快照，不保存数据或逐条记录。@since 1.3.3 */
public class OutputMetrics implements Serializable {
    private static final long serialVersionUID = 1L;
    private long count;
    private double oneMinuteRate;

    public OutputMetrics() {
    }

    public OutputMetrics(long count, double oneMinuteRate) {
        this.count = count;
        this.oneMinuteRate = oneMinuteRate;
    }

    @Counter
    @Schema(title = "数据发出数量", description = "当前 Output 的逻辑数据发出累计，条件过滤和广播前计数，不表示成功投递")
    public long getCount() {
        return count;
    }

    @EwmaRate(value = 60, tickSeconds = 5, unit = "count/s")
    @Schema(title = "数据发出速率", description = "60秒时间常数的平滑速率，5秒惰性推进，首次节拍前为0")
    public double getOneMinuteRate() {
        return oneMinuteRate;
    }
}
