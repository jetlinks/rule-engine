package org.jetlinks.rule.engine.api.events;

import java.io.Serializable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleEvent extends Serializable {

    String NODE_EXECUTE_BEFORE = "NODE_EXECUTE_BEFORE";

    String NODE_EXECUTE_FAIL = "NODE_EXECUTE_FAIL";

    String NODE_EXECUTE_DONE = "NODE_EXECUTE_DONE";

    String getEvent();

}
