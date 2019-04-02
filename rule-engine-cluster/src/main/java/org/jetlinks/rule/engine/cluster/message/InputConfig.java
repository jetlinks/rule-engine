package org.jetlinks.rule.engine.cluster.message;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
public class InputConfig implements Serializable{
    private String queue;

}
