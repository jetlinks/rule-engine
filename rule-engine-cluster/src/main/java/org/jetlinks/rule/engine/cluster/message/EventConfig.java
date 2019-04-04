package org.jetlinks.rule.engine.cluster.message;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.model.Condition;

import java.io.Serializable;

@Getter
@Setter
public class EventConfig extends OutputConfig {

    private static final long serialVersionUID = -6849794470754667710L;

    private String event;

}
