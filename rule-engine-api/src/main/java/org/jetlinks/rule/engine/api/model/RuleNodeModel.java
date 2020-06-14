package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.scheduler.SchedulingRule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 规则节点
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleNodeModel {

    private String id;

    private String ruleId;

    private String name;

    private String description;

    private String executor;

    private NodeType nodeType = NodeType.MAP;

    private SchedulingRule schedulingRule;

    private boolean end;

    private boolean start;

    private Map<String, Object> configuration = new HashMap<>();

    private List<RuleLink> events = new ArrayList<>();

    private List<RuleLink> inputs = new ArrayList<>();

    private List<RuleLink> outputs = new ArrayList<>();

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
}
