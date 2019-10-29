package org.jetlinks.rule.engine.executor.supports;

import lombok.*;

import java.io.Serializable;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TimerInfo implements Serializable {
    private static final long serialVersionUID = -2868028980853995374L;

    private String id;

    private String firstWorker;

    private String currentWorker;

    public TimerInfo current(String currentWorker){
        this.currentWorker=currentWorker;
        return this;
    }
}
