package org.jetlinks.rule.engine.executor.node.mqtt;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

@Getter
@AllArgsConstructor
public enum ClientType implements EnumDict<String> {
    producer,consumer
    ;

    @Override
    public String getValue() {
        return name();
    }

    @Override
    public String getText() {
        return name();
    }
}
