package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.rule.engine.api.scheduler.SchedulingRule;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.TaskExecutorProvider;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则节点模型,用于描述一个规则节点。
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleNodeModel {

    /**
     * 节点ID
     */
    private String id;

    /**
     * 规则ID
     *
     * @see RuleModel#getId()
     */
    private String ruleId;

    /**
     * 节点名称
     */
    private String name;

    /**
     * 节点描述
     */
    private String description;

    /**
     * 执行器标识
     *
     * @see TaskExecutorProvider#getExecutor()
     */
    private String executor;

    /**
     * 配置信息,不同的执行器,配置信息不同
     *
     * @see ExecutionContext#getJob()
     * @see SchedulingRule#getConfiguration()
     */
    private Map<String, Object> configuration = new HashMap<>();

    /**
     * 调度规则
     */
    private SchedulingRule schedulingRule;

    /**
     * 是否为结束节点
     */
    private boolean end;

    /**
     * 是否为开始节点
     */
    private boolean start;

    /**
     * 事件连接,表示监听此节点的事件,并将事件连接到其他节点。事件标识由{@link RuleLink#getType()}定义.
     *
     * @see org.jetlinks.rule.engine.api.RuleConstants.Event
     * @see RuleLink#getType()
     */
    private List<RuleLink> events = new ArrayList<>();

    /**
     * 此节点的输入节点
     */
    private List<RuleLink> inputs = new ArrayList<>();

    /**
     * 此节点的输出节点
     */
    private List<RuleLink> outputs = new ArrayList<>();

    /**
     * 是否并行执行
     *
     * @deprecated 已弃用
     */
    @Deprecated
    private boolean parallel;

    public RuleNodeModel addConfiguration(String key, Object value) {
        configuration.put(key, value);
        return this;
    }

    public List<RuleLink> getEvents(String type) {
        return events.stream()
                     .filter(link -> type.equals(link.getType()))
                     .collect(Collectors.toList());
    }

    public boolean isStartNode() {
        return start || inputs == null || inputs.isEmpty();
    }

    public boolean isEndNode() {
        return end || outputs == null || outputs.isEmpty();
    }

    private void appendMermaidNode(StringBuilder builder) {
        builder.append(getId());

        builder.append(isStartNode() || isEndNode() ? "[" : "(");

        if (StringUtils.hasText(name)) {
            builder.append(name);
        } else {
            builder.append(executor);
        }

        builder.append(isStartNode() || isEndNode() ? "]" : ")");
    }

    public void appendMermaid(StringBuilder builder) {
        if (CollectionUtils.isNotEmpty(outputs)) {
            int index = 0;
            for (RuleLink output : outputs) {
                if (index++ > 0) {
                    builder.append("\n");
                }
                appendMermaidNode(builder);
                builder.append("-->");
                if (StringUtils.hasText(output.getDescription())) {
                    builder.append("|")
                           .append(output.getDescription())
                           .append("|");
                }
                output.getTarget()
                      .appendMermaidNode(builder);

            }
        } else {
            appendMermaidNode(builder);
        }
    }

    @Override
    public String toString() {
        // timer(定时)-->
        StringBuilder builder = new StringBuilder();
        appendMermaid(builder);
        return builder.toString();
    }
}
