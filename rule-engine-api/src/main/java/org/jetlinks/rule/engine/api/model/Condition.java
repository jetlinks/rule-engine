package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * 规则条件
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class Condition {

    private String type;

    private Map<String, Object> configuration;
    
}
