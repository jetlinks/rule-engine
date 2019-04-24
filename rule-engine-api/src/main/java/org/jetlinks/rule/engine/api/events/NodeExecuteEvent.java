package org.jetlinks.rule.engine.api.events;

import lombok.*;
import org.jetlinks.rule.engine.api.RuleData;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class NodeExecuteEvent implements RuleEvent {

    private String event;

    private String instanceId;

    private String nodeId;

    private RuleData ruleData;

}
