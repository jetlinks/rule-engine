package org.jetlinks.rule.engine.api;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.bean.FastBeanCopier;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

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
    public void removeAttribute(String key) {
        attributes.remove(key);
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
    @SuppressWarnings("all")
    public void acceptMap(Consumer<Map<String, Object>> consumer) {
        if(data==null){
            return;
        }
        else if(data instanceof byte[]){
            data =JSON.parse(((byte[]) data));
        }
        else if(data instanceof String){
            data =JSON.parse(((String) data));
        }
        else if(data instanceof Map){
          doAcceptMap(data,consumer);
        }
        else if(data instanceof Collection){
            ((Collection) data).forEach(d->doAcceptMap(d,consumer));
        }else{
            doAcceptMap(data,consumer);
        }
    }

    @SuppressWarnings("all")
    private void doAcceptMap(Object data,Consumer<Map<String, Object>> consumer){
        if(data instanceof Map){
            consumer.accept(((Map) data));
        }else{
            consumer.accept(FastBeanCopier.copy(data,HashMap::new));
        }
    }

    @Override
    public RuleData newData(Object data) {
        if (data instanceof RuleData) {
            data = ((RuleData) data).getData();
        }
        DefaultRuleData ruleData = new DefaultRuleData();
        ruleData.data = data;
        ruleData.id = id;
        ruleData.attributes = new HashMap<>(attributes);
        return ruleData;
    }

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
