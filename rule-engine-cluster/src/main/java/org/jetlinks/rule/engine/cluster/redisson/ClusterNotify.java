package org.jetlinks.rule.engine.cluster.redisson;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor

public class ClusterNotify implements Serializable {

    private String address;

    private Object message;

}