package org.jetlinks.rule.engine.api.model;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

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
    
}
