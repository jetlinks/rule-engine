package org.jetlinks.rule.engine.api;

import org.hswebframework.web.id.IDGenerator;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleData extends Serializable {
    String getId();

    Object getData();

    RuleData newData(Object data);

    Map<String, Object> getAttributes();

    Optional<Object> getAttribute(String key);

    void setAttribute(String key, Object value);

    void clear();

    static RuleData create(Object data) {
        DefaultRuleData ruleData = new DefaultRuleData();
        ruleData.setId(IDGenerator.MD5.generate());
        ruleData.setData(data);

        return ruleData;
    }
}
