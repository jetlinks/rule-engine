package org.jetlinks.rule.engine.api.stream;

import java.io.Serializable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface StreamData extends Serializable {
    String getId();

    Object getData();

    StreamData newData(Object data);
}
