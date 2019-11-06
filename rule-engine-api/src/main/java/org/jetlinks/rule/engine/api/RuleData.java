package org.jetlinks.rule.engine.api;

import org.hswebframework.web.id.IDGenerator;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 规则数据
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleData extends Serializable {
    String getId();

    Object getData();

    void acceptMap(Consumer<Map<String, Object>> consumer);

    Flux<Map<String,Object>> dataToMap();

    RuleData newData(Object data);

    RuleData copy();

    Map<String, Object> getAttributes();

    Optional<Object> getAttribute(String key);

    void setAttribute(String key, Object value);

    void removeAttribute(String key);

    void clear();

    static RuleData create(Object data) {
        DefaultRuleData ruleData = new DefaultRuleData();
        ruleData.setId(IDGenerator.MD5.generate());
        ruleData.setData(data);
        return ruleData;
    }
}
