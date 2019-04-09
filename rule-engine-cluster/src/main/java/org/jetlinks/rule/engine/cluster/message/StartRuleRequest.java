package org.jetlinks.rule.engine.cluster.message;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class StartRuleRequest implements Serializable {

    private List<String> inputQueue;

    private String instanceId;

    private String ruleId;

}
