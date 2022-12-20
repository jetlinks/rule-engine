package org.jetlinks.rule.engine.api;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.bean.FastBeanCopier;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.core.metadata.Jsonable;
import reactor.core.publisher.Flux;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * 规则数据,用于在规则之间传递数据
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleData implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final String RECORD_DATA_TO_HEADER = "record_data_to_header";

    public static final String RECORD_DATA_TO_HEADER_KEY = "record_data_to_header_key";

    public static final String RECORD_DATA_TO_HEADER_KEY_PREFIX = "rd:";

    /**
     * 数据ID
     */
    private String id;

    /**
     * 上下文ID,在一条数据创建时生成,在传递过程中此ID不变
     */
    private String contextId;

    /**
     * 真实数据
     */
    private Object data;

    /**
     * 规则头信息,可以通过头信息来传递更多的拓展消息
     */
    @Getter
    private Map<String, Object> headers = new ConcurrentHashMap<>();

    public void setHeader(String key, Object value) {
        headers.put(key, value);
    }

    public void removeHeader(String key) {
        headers.remove(key);
    }

    public void clearHeader() {
        headers.clear();
    }

    public Optional<Object> getHeader(String key) {
        return Optional.ofNullable(headers.get(key));
    }

    public String getId() {
        return id;
    }

    public Object getData() {
        return data;
    }

    public Flux<Map<String, Object>> dataToMap() {
        return Flux.create(sink -> {
            acceptMap(sink::next);
            sink.complete();
        });
    }

    @SuppressWarnings("all")
    public void acceptMap(Consumer<Map<String, Object>> consumer) {
        Object data = this.data;
        if (data == null) {
            return;
        } else if (data instanceof byte[]) {
            byte[] bytes = ((byte[]) data);
            if (bytes.length > 2) {
                if (/* { }*/(bytes[0] == 123 && bytes[bytes.length - 1] == 125)
                        || /* [ ] */(bytes[0] == 91 && bytes[bytes.length - 1] == 93)
                ) {
                    data = JSON.parse(bytes);
                }
            }
        } else if (data instanceof String) {
            String stringData = (String) data;
            if (stringData.startsWith("{") || stringData.startsWith("[")) {
                data = JSON.parse(stringData);
            }
        }

        if (data instanceof Map) {
            doAcceptMap(data, consumer);
        } else if (data instanceof RuleData) {
            ((RuleData) data).acceptMap(consumer);
        } else if (data instanceof Iterable) {
            ((Iterable) data).forEach(d -> doAcceptMap(d, consumer));
        } else {
            doAcceptMap(data, consumer);
        }
    }

    @SuppressWarnings("all")
    private void doAcceptMap(Object data, Consumer<Map<String, Object>> consumer) {
        if (data == null) {
            return;
        }
        if (data instanceof Map) {
            consumer.accept(((Map) data));
        } else if (data instanceof Jsonable) {
            consumer.accept(((Jsonable) data).toJson());
        } else {
            consumer.accept(FastBeanCopier.copy(data, HashMap::new));
        }
    }

    public RuleData newData(Object data) {
        RuleData ruleData = new RuleData();
        if (data instanceof RuleData) {
            data = ((RuleData) data).getData();
        }
        ruleData.id = IDGenerator.RANDOM.generate();
        ruleData.headers.putAll(headers);
        ruleData.data = data;
        ruleData.contextId = contextId;
        RuleDataHelper.clearError(ruleData);
        return ruleData;
    }

    public RuleData copy() {
        RuleData ruleData = new RuleData();
        ruleData.id = id;
        ruleData.contextId = contextId;
        ruleData.headers.putAll(headers);
        ruleData.data = data;
        return ruleData;
    }

    public static RuleData create(Object data) {
        if (data instanceof RuleData) {
            return ((RuleData) data).newData(data);
        }
        RuleData ruleData = new RuleData();
        ruleData.setId(IDGenerator.RANDOM.generate());
        ruleData.setContextId(IDGenerator.RANDOM.generate());
        ruleData.setData(data);
        return ruleData;
    }
}
