package org.jetlinks.rule.engine.cluster.logger;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Getter
@Setter
public class LogInfo implements Serializable {

    private String instanceId;

    private String nodeId;

    private String level;

    private String message;

    private long timestamp;

    private List<String> args;

    private Map<String, String> context;

}
