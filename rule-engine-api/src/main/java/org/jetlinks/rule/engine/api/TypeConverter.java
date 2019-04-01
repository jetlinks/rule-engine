package org.jetlinks.rule.engine.api;

import java.util.List;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface TypeConverter {

    <T> T convert(Object data, Class<T> type);

    <T> List<T> convertList(Object data, Class<T> elementType);

}
