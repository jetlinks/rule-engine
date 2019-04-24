package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.rule.engine.cluster.logger.LogInfo;

@Getter
@AllArgsConstructor
public class NodeExecuteLogEvent {
    private LogInfo logInfo;
}
