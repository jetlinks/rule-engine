package org.jetlinks.rule.engine.cluster.task;

import lombok.*;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.ScheduleJob;
import org.jetlinks.rule.engine.api.Task;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MockTask implements Task {
    String id;

    String name;

    String workerId;

    String schedulerId;

    ScheduleJob job;

    @Builder.Default
    State state = State.shutdown;
    @Builder.Default
    long lastStateTime = System.currentTimeMillis();
    @Builder.Default
    long startTime = System.currentTimeMillis();

    boolean debug;
    @Override
    public Mono<Void> setJob(ScheduleJob job) {
        this.job = job;
        return Mono.empty();
    }

    @Override
    public Mono<Void> reload() {
        return Mono.empty();
    }

    @Override
    public Mono<Void> start() {
        state = State.running;
        return Mono.empty();
    }

    @Override
    public Mono<Void> pause() {
        state = State.paused;
        return Mono.empty();
    }

    @Override
    public Mono<Void> shutdown() {
        state = State.shutdown;
        return Mono.empty();
    }

    @Override
    public Mono<Void> execute(Publisher<RuleData> data) {

        return Mono.empty();
    }

    @Override
    public Mono<State> getState() {
        return Mono.just(state);
    }

    @Override
    public Mono<Void> debug(boolean debug) {
        this.debug=debug;
        return Mono.empty();
    }

    @Override
    public Mono<Long> getLastStateTime() {
        return Mono.just(lastStateTime);
    }

    @Override
    public Mono<Long> getStartTime() {
        return Mono.just(startTime);
    }
}
