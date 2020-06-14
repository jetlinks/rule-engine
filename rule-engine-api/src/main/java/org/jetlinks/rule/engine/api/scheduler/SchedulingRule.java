package org.jetlinks.rule.engine.api.scheduler;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 调度规则
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class SchedulingRule implements Serializable {
    private String type;

    private Map<String, Object> configuration;
}
