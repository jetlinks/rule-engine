package org.jetlinks.rule.engine.api.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.Condition;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.api.task.TaskExecutorProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 调度任务,在规则发布时,会将规则节点{@link org.jetlinks.rule.engine.api.model.RuleNodeModel}转为任务,发送给对应的调度器{@link Scheduler}进行调度执行
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
     *
     * @see RuleNodeModel#getRuleId()
     */
    @Nonnull
    private String ruleId;

    /**
     * 节点ID
     *
     * @see RuleNodeModel#getId()
     */
    @Nonnull
    private String nodeId;

    /**
     * 任务名称
     */
    private String name;

    /**
     * 执行器
     *
     * @see TaskExecutorProvider#getExecutor()
     * @see RuleNodeModel#getExecutor()
     */
    @Nonnull
    private String executor;

    /**
     * 执行器配置信息
     *
     * @see RuleNodeModel#getConfiguration()
     */
    private Map<String, Object> configuration;

    /**
     * 输入节点
     *
     * @see RuleNodeModel#getId()
     * @see RuleNodeModel#getInputs()
     */
    private List<String> inputs = new ArrayList<>();

    /**
     * 监听事件输入
     */
    private List<Event> events = new ArrayList<>();

    /**
     * 监听事件输出
     */
    private List<Event> eventOutputs = new ArrayList<>();

    /**
     * 输出节点
     */
    private List<Output> outputs = new ArrayList<>();

    /**
     * 上下文
     */
    private Map<String, Object> context = new HashMap<>();

    /**
     * 调度规则
     */
    private SchedulingRule schedulingRule;

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Event implements Serializable {

        /**
         * 事件类型
         *
         * @see RuleLink#getType()
         */
        @Nonnull
        private String type;

        /**
         * 事件源
         *
         * @see RuleNodeModel#getId()
         * @see RuleLink#getSource()
         */
        @Nonnull
        private String source;

    }

    @Getter
    @Setter
    @AllArgsConstructor
    @NoArgsConstructor
    public static class Output implements Serializable {
        /**
         * 输出节点
         *
         * @see RuleNodeModel#getId()
         */
        @Nonnull
        private String output;

        /**
         * 输出条件,满足条件才输出
         */
        private Condition condition;
    }

    @Override
    public String toString() {
        return instanceId + ":" + nodeId + "(" + executor + ")";
    }
}
