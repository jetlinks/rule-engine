package org.jetlinks.rule.engine.api;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.bean.FastBeanCopier;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.core.GenericHeaderSupport;
import org.jetlinks.core.Routable;
import org.jetlinks.core.message.Headers;
import org.jetlinks.core.metadata.Jsonable;
import org.jetlinks.core.utils.SerializeUtils;
import reactor.core.publisher.Flux;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/**
 * 规则数据,用于在规则之间传递数据
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleData extends GenericHeaderSupport<RuleData> implements Externalizable, Routable {

    private static final long serialVersionUID = 1L;

    public static final String RECORD_DATA_TO_HEADER = "record_data_to_header";

    public static final String RECORD_DATA_TO_HEADER_KEY = "record_data_to_header_key";

    public static final String RECORD_DATA_TO_HEADER_KEY_PREFIX = "rd:";

    public static final String HEADER_SOURCE_NODE_ID = "sourceNode";

    static {
        SerializeUtils.registerSerializer(0x50, RuleData.class, ignore -> new RuleData());
    }

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

    public void setHeader(String key, Object value) {
        if (key == null || value == null) {
            return;
        }
        addHeader(key, value);
    }

    @SuppressWarnings("all")
    public Flux<Map<String, Object>> dataToMap() {
        Object data = this.data;
        if (data instanceof Map) {
            return Flux.just(((Map) data));
        }
        if (data instanceof Jsonable) {
            return Flux.just(((Jsonable) data).toJson());
        }
        if (data instanceof RuleData) {
            return ((RuleData) data).dataToMap();
        }
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
            consumer.accept(((Map) data));
        } else if (data instanceof Jsonable) {
            consumer.accept(((Jsonable) data).toJson());
        } else if (data instanceof RuleData) {
            ((RuleData) data).acceptMap(consumer);
        } else if (data instanceof Iterable) {
            for (Object datum : ((Iterable) data)) {
                doAcceptMap(datum, consumer);
            }
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
        if (getHeaders() != null) {
            getHeaders().forEach(ruleData::addHeader);
        }
        ruleData.data = data;
        ruleData.contextId = contextId;
        RuleDataHelper.clearError(ruleData);
        return ruleData;
    }

    public RuleData copy() {
        RuleData ruleData = new RuleData();
        ruleData.id = id;
        ruleData.contextId = contextId;
        if (getHeaders() != null) {
            getHeaders().forEach(ruleData::addHeader);
        }
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

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        SerializeUtils.writeObject(id, out);
        SerializeUtils.writeObject(contextId, out);
        SerializeUtils.writeObject(data, out);
        SerializeUtils.writeKeyValue(getHeaders(), out);
    }

    @Override
    @SuppressWarnings("all")
    public void readExternal(ObjectInput in) throws IOException, ClassNotFoundException {
        id = (String) SerializeUtils.readObject(in);
        contextId = (String) SerializeUtils.readObject(in);
        data = SerializeUtils.readObject(in);
        SerializeUtils.readKeyValue(in, this::addHeader);
    }

    @Override
    public Object routeKey() {
        return this
            .getHeader(Headers.routeKey)
            .orElseGet(() -> data instanceof Routable ? ((Routable) data).routeKey() : contextId);
    }

    @Override
    public long hash(Object... objects) {
        if (data instanceof Routable) {
            return ((Routable) data).hash(objects);
        }
        return Routable.super.hash(objects);
    }
}
