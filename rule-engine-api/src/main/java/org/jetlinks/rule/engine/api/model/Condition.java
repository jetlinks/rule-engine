package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

/**
 * 规则条件
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class Condition implements Serializable {

    private static final long serialVersionUID = -6849794470754667710L;

    /**
     * 条件类型,不同的条件类型支持不同的处理方式,由具体的规则引擎实现
     */
    private String type;

    /**
     * 条件配置,不同的条件类型配置不同
     */
    private Map<String, Object> configuration;

    @SuppressWarnings("all")
    public <T> Optional<T> getConfig(String key) {
        return Optional
                .ofNullable(configuration)
                .map(cfg -> (T) cfg.get(key));
    }

}
