package org.jetlinks.rule.engine.cluster;

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

    private NodeRule[] rules;

    private long uptime;

    private long lastKeepAliveTime;

}
