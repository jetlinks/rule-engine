package org.jetlinks.rule.engine.api.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.Condition;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 调度任务
 *
 * @author zhouhao
 * @since 1.0
 */
@Getter
@Setter
public class ScheduleJob implements Serializable {

    private static final long serialVersionUID = -1L;

    /**
     * 规则实例ID
     */
    @Nonnull
    private String instanceId;

    /**
     * 规则ID
     */
    @Nonnull
    private String ruleId;

    /**
     * 节点ID
     */
    @Nonnull
    private String nodeId;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 执行器
     */
    @Nonnull
    private String executor;

    /**
     * 执行器配置信息
     */
    private Map<String, Object> configuration;

    /**
     * 输入节点
     */
    private List<String> inputs = new ArrayList<>();

    /**
     * 监听事件输入
     */
    private List<Event> events = new ArrayList<>();

    /**
     * 输出节点
     */
    private List<Output> outputs = new ArrayList<>();

    /**
     * 上下文
     */
    private Map<String, Object> context;

    /**
     * 调度规则
     */
    private SchedulingRule schedulingRule;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Event implements Serializable {

        private String type;

        private String source;

    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Output implements Serializable {
        private String output;

        private Condition condition;
    }

    @Override
    public String toString() {
        return instanceId + ":" + nodeId + "(" + executor + ")";
    }
}
