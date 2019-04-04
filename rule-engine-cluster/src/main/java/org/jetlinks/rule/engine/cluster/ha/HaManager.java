package org.jetlinks.rule.engine.cluster.ha;

import org.jetlinks.rule.engine.cluster.NodeInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface HaManager {
    NodeInfo getCurrentNode();

    List<NodeInfo> getAllAliveNode();

    HaManager onNodeJoin(Consumer<NodeInfo> consumer);

    HaManager onNodeLeave(Consumer<NodeInfo> consumer);

}
