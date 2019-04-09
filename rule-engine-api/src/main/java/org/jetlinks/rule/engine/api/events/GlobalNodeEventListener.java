package org.jetlinks.rule.engine.api.events;

import org.jetlinks.rule.engine.api.RuleData;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface GlobalNodeEventListener {
    void onEvent(NodeExecuteEvent executeEvent);
}
