package org.jetlinks.rule.engine.api.executor;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
@EqualsAndHashCode
public class RuleNodeConfiguration implements Serializable {
    private String id;

    private String name;

    private String type;

    private Map<String, Object> configuration;
}
