package org.jetlinks.rule.engine.api;

import lombok.Getter;
import lombok.Setter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class DefaultRuleData implements RuleData {
    private static final long serialVersionUID = -6849794470754667710L;

    private String id;

    private Object data;

    @Getter
    private Map<String, Object> attributes = new HashMap<>();

    @Override
    public void setAttribute(String key, Object value) {
        attributes.put(key, value);
    }

    @Override
    public void clear() {
        attributes.clear();
    }

    @Override
    public Optional<Object> getAttribute(String key) {
        return Optional.ofNullable(attributes.get(key));
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public Object getData() {
        return data;
    }

    @Override
    public RuleData newData(Object data) {
        DefaultRuleData ruleData = new DefaultRuleData();
        ruleData.data = data;
        ruleData.id = id;
        ruleData.attributes = attributes;
        return ruleData;
    }
}
