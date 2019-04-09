package org.jetlinks.rule.engine.api.persistent;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleInstancePersistent implements Serializable {
    private String id;

    private String ruleId;

    private String schedulerNodeId;

    private Date createTime;

    private Boolean running;

    private String instanceDetailJson;

}
