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

    private NodeRule[] rules = {NodeRule.SCHEDULER, NodeRule.WORKER, NodeRule.MONITOR};

    private long uptime;

    private long lastKeepAliveTime;

}
