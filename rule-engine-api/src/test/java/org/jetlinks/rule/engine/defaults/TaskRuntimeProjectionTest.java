package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.TaskRuntimeSnapshot;
import org.jetlinks.rule.engine.defaults.scope.InMemoryGlobalScope;
import org.jetlinks.supports.event.InternalEventBus;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class TaskRuntimeProjectionTest {
    @Test
    public void lightweightProjectionKeepsRolesAndMetricsWithoutSerializingConfiguration() throws Exception {
        var job = new ScheduleJob();
        job.setInstanceId("rule");
        job.setNodeId("entry");
        job.setExecutor("test");
        job.setInputs(List.of("other"));
        job.setConfiguration(Map.of("secret", "must-not-ship-runtime-configuration"));
        var context = new DefaultExecutionContext("worker", job, new InternalEventBus(),
            (condition, data) -> true, new InMemoryGlobalScope());
        var executor = new FunctionTaskExecutor("test", context) {
            @Override protected Mono<RuleData> apply(RuleData input) { return Mono.just(input); }
        };
        var task = new DefaultTask("scheduler", "worker", context, executor);
        task.execute(RuleData.create("input")).block();
        TaskRuntimeSnapshot snapshot = task.runtime().block();
        assertNotNull(snapshot);
        assertEquals("rule", snapshot.getInstanceId());
        assertEquals(Boolean.FALSE, snapshot.getEntry());
        assertEquals(Boolean.TRUE, snapshot.getTerminal());
        assertEquals(1, snapshot.getOutputMetrics().getCount());
        assertEquals(1, snapshot.getMetrics().getExecutionCount());
        var bytes = new ByteArrayOutputStream();
        try (var output = new ObjectOutputStream(bytes)) { output.writeObject(snapshot); }
        assertFalse(bytes.toString(StandardCharsets.ISO_8859_1).contains("must-not-ship-runtime-configuration"));
        job.setInputs(List.of());
        assertEquals(Boolean.TRUE, task.runtime().block().getEntry());
        job.setEvents(List.of(new ScheduleJob.Event("complete", "upstream")));
        assertEquals(Boolean.FALSE, task.runtime().block().getEntry());
        job.setEventOutputs(List.of(new ScheduleJob.Event("complete", "downstream")));
        assertEquals(Boolean.FALSE, task.runtime().block().getTerminal());
        job.setEventOutputs(List.of());
        job.setOutputs(List.of(new ScheduleJob.Output("downstream", null)));
        assertEquals(Boolean.FALSE, task.runtime().block().getTerminal());
        task.shutdown().block();
    }

    @Test
    public void stoppedRuleQueriesDoNotRetainEmptyIndexesAndScopeIsBounded() throws Exception {
        var scheduler = new LocalScheduler("scheduler");
        for (int i = 0; i < 1000; i++) scheduler.getRuntime(List.of("stopped-" + i)).block();
        var field = LocalScheduler.class.getDeclaredField("executors");
        field.setAccessible(true);
        assertTrue(((Map<?, ?>) field.get(scheduler)).isEmpty());
        StepVerifier.create(scheduler.getRuntime(java.util.Collections.nCopies(65, "rule")))
            .expectError(IllegalArgumentException.class).verify();
        StepVerifier.create(scheduler.getRuntime(List.of("")))
            .expectError(IllegalArgumentException.class).verify();
    }
}
