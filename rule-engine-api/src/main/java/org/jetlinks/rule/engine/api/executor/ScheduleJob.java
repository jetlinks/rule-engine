package org.jetlinks.rule.engine.api.executor;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.Condition;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 调度任务
 */
@Getter
@Setter
public class ScheduleJob implements Serializable {

    private static final long serialVersionUID = -1L;

    /**
     * 任务唯一标识
     */
    @Nonnull
    private String id;

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
    private List<String> inputs;

    /**
     * 输出节点
     */
    private List<Output> outputs;

    /**
     * 事件输出ID
     */
    private Map<String, List<String>> events;

    /**
     * 上下文
     */
    private Map<String, Object> context;


    @Getter
    @Setter
    public static class Output implements Serializable{
        private String output;

        private Condition condition;
    }

}
