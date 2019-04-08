package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

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

    private String id;

    private String name;

    private String description;

    private Map<String, Object> configuration = new HashMap<>();

    private List<RuleLink> events = new ArrayList<>();

    private List<RuleNodeModel> nodes = new ArrayList<>();

    public List<RuleLink> getEvents(String type) {
        return events.stream()
                .filter(link -> type.equals(link.getType()))
                .collect(Collectors.toList());
    }

    public Optional<RuleNodeModel> getNode(String nodeId){
        return nodes.stream()
                .filter(model->model.getId().equals(nodeId))
                .findFirst();
    }

    public RuleModel addConfiguration(String key, Object value) {
        configuration.put(key, value);
        return this;
    }

    public Optional<RuleNodeModel> getStartNode() {
        return nodes.stream()
                .filter(RuleNodeModel::isStartNode)
                .findFirst();
    }

    public List<RuleNodeModel> getEndNodes() {
        return nodes.stream()
                .filter(RuleNodeModel::isEndNode)
                .collect(Collectors.toList());
    }
}
