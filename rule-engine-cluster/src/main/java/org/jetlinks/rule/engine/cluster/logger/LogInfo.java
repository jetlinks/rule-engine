package org.jetlinks.rule.engine.cluster.logger;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;


@Getter
@Setter
public class LogInfo implements Serializable {

    private String level;

    private String message;

    private Map<String,String> context;

}
