package org.jetlinks.rule.engine.api.events;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Deprecated
public interface GlobalNodeEventListener {
    void onEvent(NodeExecuteEvent executeEvent);
}
