package org.jetlinks.rule.engine.api.persistent;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.Rule;

import java.io.Serializable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RulePersistent implements Serializable {

    private String id;

    private String ruleId;

    private Integer version;

    private String name;

    private String modelFormat;

    private String model;


}
