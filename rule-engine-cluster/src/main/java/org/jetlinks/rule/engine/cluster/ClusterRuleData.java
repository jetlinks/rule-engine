package org.jetlinks.rule.engine.cluster;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class ClusterRuleData implements RuleData {

    @Getter
    @Setter
    private String id;

    @Getter
    @Setter
    private Object data;

    private transient ClusterMap<String, Object> attributeMap;

    public void init(ClusterManager clusterManager) {
        attributeMap = clusterManager.getMap("cluster:data:attr:" + id);
    }

    @Override
    public RuleData newData(Object data) {
        ClusterRuleData ruleData = new ClusterRuleData();
        ruleData.id = id;
        ruleData.data = data;
        ruleData.attributeMap = attributeMap;
        return ruleData;
    }

    @Override
    public Map<String, Object> getAttributes() {
        if (attributeMap != null) {
            return attributeMap.toMap();
        }
        return new HashMap<>();
    }

    @Override
    public Optional<Object> getAttribute(String key) {
        return Optional.ofNullable(attributeMap)
                .flatMap(cmap -> cmap.get(key));
    }

    public static ClusterRuleData of(RuleData ruleData, ClusterManager clusterManager) {
        ClusterRuleData data = new ClusterRuleData();
        data.id = ruleData.getId();
        data.data = ruleData.getData();
        data.init(clusterManager);
        data.attributeMap.putAll(ruleData.getAttributes());
        return data;
    }

    @Override
    public void clear() {
        if (attributeMap != null) {
            attributeMap.clear();
        }
    }

    @Override
    public void setAttribute(String key, Object value) {
        if (attributeMap != null) {
            attributeMap.put(key, value);
        }
    }
}
