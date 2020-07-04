package org.jetlinks.rule.engine.defaults;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LogEvent {

    private String instanceId;

    private String nodeId;

    private String workerId;

    private String level;

    private String message;

    private String exception;

    private long timestamp;

}
