package org.jetlinks.rule.engine.api.scheduler;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Map;

/**
 * 调度规则
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
public class SchedulingRule implements Serializable {
    /**
     * 调度规则类型标识
     */
    private String type;

    /**
     * 调度配置
     */
    private Map<String, Object> configuration;
}
