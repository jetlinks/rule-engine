package org.jetlinks.rule.engine.api.persistent;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;

import java.io.Serializable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class RuleHistoryPersistent implements Serializable {
    private String id;

    private String instanceId;

    private String ruleId;

    private String nodeId;

    private String dataId;

    private String dataJson;

    private String errorType;

    private String errorInfo;

    private Boolean success;
}
