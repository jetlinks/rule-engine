package org.jetlinks.rule.engine.cluster.message;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class InitRuleNodeError implements Serializable {
    private static final long serialVersionUID = -6849794470754667710L;

    private String instanceId;

    private String nodeId;

    private String errorType;

    private String errorMessage;
}
