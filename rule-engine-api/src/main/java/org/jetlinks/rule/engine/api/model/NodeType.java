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

    //转换新的数据
    MAP("MAP", true),
    //仅消费数据
    PEEK("PEEK", false);

    private String text;
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
