package org.jetlinks.rule.engine.api.cluster;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@ToString
public class NodeInfo implements Serializable {
    private String id;

    private String name;

    private String[] tags;

    private NodeRole[] rules = {NodeRole.SCHEDULER, NodeRole.WORKER, NodeRole.MONITOR};

    private long uptime;

    private long lastKeepAliveTime;

}
