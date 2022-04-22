package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.scheduler.SchedulingRule;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 规则模型
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleModel {

    /**
     * 规则模型ID
     */
    private String id;

    /**
     * 规则名称
     */
    private String name;

    /**
     * 模型类型
     */
    private String type;

    /**
     * 描述
     */
    private String description;

    /**
     * 调度规则
     */
    private SchedulingRule schedulingRule;

    /**
     * 规则配置
     */
    private Map<String, Object> configuration = new HashMap<>();

    /**
     * 规则事件连接
     */
    private List<RuleLink> events = new ArrayList<>();

    /**
     * 规则节点,包含所有的节点
     */
    private List<RuleNodeModel> nodes = new ArrayList<>();

    public RuleLink link(RuleNodeModel source, RuleNodeModel target) {
        RuleLink link = new RuleLink();
        link.setId(source.getId() + ":" + target.getId());
        link.setName(source.getName() + ":" + target.getName());

        link.setSource(source);
        link.setTarget(target);

        source.getOutputs().add(link);
        source.getInputs().add(link);

        return link;
    }

    public List<RuleLink> getEvents(String type) {
        return events.stream()
                     .filter(link -> type.equals(link.getType()))
                     .collect(Collectors.toList());
    }

    public Optional<RuleNodeModel> getNode(String nodeId) {
        return nodes.stream()
                    .filter(model -> model.getId().equals(nodeId))
                    .findFirst();
    }

    public RuleModel addConfiguration(String key, Object value) {
        configuration.put(key, value);
        return this;
    }

    public Optional<RuleNodeModel> getStartNode() {
        return nodes.stream()
                    .filter(RuleNodeModel::isStart)
                    .findFirst();
    }

    public List<RuleNodeModel> getEndNodes() {
        return nodes.stream()
                    .filter(RuleNodeModel::isEnd)
                    .collect(Collectors.toList());
    }
}
