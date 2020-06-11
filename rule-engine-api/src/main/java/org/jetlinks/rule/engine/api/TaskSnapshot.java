package org.jetlinks.rule.engine.api;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.executor.ScheduleJob;

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

    private String instanceId;

    private String workerId;

    private ScheduleJob job;

    private String name;

    private long startTime;

    private long lastStateTime;

    private Task.State state;


}
