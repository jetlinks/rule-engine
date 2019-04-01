package org.jetlinks.rule.engine.api.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@AllArgsConstructor
public enum NodeType implements EnumDict<String> {
    MAP("MAP", true),
    PEEK("PEEK", false);

    private String  text;
    private boolean returnNewValue;

    @Override
    public String getValue() {
        return name();
    }

    @Override
    public boolean isWriteJSONObjectEnabled() {
        return false;
    }
}
