package org.jetlinks.rule.engine.cluster;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class NodeInfo implements Serializable {
    private String id;

    private String name;

    private String[] tags;

    private NodeRule[] rules;

    private long uptime;

}
