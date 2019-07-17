package org.jetlinks.rule.engine.api;

import lombok.AllArgsConstructor;
import org.hswebframework.web.dict.EnumDict;

@AllArgsConstructor
public enum RuleInstanceState implements EnumDict<String> {

    initializing(),
    initializeFailed(),
    initialized(),

    starting(),
    startFailed(),
    started(),

    stopping(),
    stopFailed(),
    stopped();

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
