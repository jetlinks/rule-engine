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

    private String type;

    private Map<String, Object> configuration;

    @SuppressWarnings("all")
    public <T> Optional<T> getConfig(String key) {
        return Optional
                .ofNullable(configuration)
                .map(cfg -> (T) cfg.get(key));
    }

}
