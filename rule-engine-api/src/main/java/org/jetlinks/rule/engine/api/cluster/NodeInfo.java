package org.jetlinks.rule.engine.api.cluster;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hswebframework.web.dict.EnumDict;

import java.io.Serializable;

@Getter
@Setter
@ToString
@EqualsAndHashCode(of = "id")
public class NodeInfo implements Serializable {
    private String id;

    private String name;

    private String[] tags;

    private NodeRole[] roles = {NodeRole.SCHEDULER, NodeRole.WORKER, NodeRole.MONITOR};

    private long uptime;

    public boolean hasRole(NodeRole role){
        return EnumDict.in(role,roles);
    }

    public boolean isWorker(){
        return hasRole(NodeRole.WORKER);
    }

    public boolean isScheduler(){
        return hasRole(NodeRole.SCHEDULER);
    }

}
