package org.jetlinks.rule.engine.cluster.logger;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;
import java.util.Map;


@Getter
@Setter
public class LogInfo implements Serializable {

    private String level;

    private String message;

    private List<String> args;

    private Map<String, String> context;

}
