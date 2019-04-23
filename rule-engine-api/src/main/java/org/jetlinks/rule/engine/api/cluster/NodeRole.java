package org.jetlinks.rule.engine.api.cluster;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

@Getter
@AllArgsConstructor
public enum NodeRole implements EnumDict<String> {
    SCHEDULER,
    MONITOR,
    WORKER;

    @Override
    public String getValue() {
        return name();
    }

    @Override
    public String getText() {
        return name();
    }

    @Override
    public boolean isWriteJSONObjectEnabled() {
        return false;
    }
}
