package org.jetlinks.rule.engine.api;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

/**
 * 任务快照
 *
 * @author zhouhao
 * @since 1.0.4
 */
@Getter
@Setter
public class TaskSnapshot implements Serializable {

    private String id;

    private String instanceId;

    private String schedulerId;

    private String workerId;

    private ScheduleJob job;

    private String name;

    private long startTime;

    private long lastStateTime;

    private Task.State state;


}
