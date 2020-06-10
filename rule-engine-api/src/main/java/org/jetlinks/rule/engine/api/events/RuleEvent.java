package org.jetlinks.rule.engine.api.events;

import java.io.Serializable;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Deprecated
public interface RuleEvent extends Serializable {

    String NODE_EXECUTE_BEFORE = "NODE_EXECUTE_BEFORE";

    String NODE_EXECUTE_FAIL = "NODE_EXECUTE_FAIL";

    String NODE_EXECUTE_DONE = "NODE_EXECUTE_DONE";

    String NODE_EXECUTE_RESULT = "NODE_EXECUTE_RESULT";

    String NODE_STARTED = "NODE_STARTED";


    String getEvent();

}
