package org.jetlinks.rule.engine.defaults;

import org.jetlinks.core.monitor.observation.ObservationData;
import org.jetlinks.core.monitor.observation.ObservationKeys;
import org.jetlinks.core.monitor.observation.ObservationReference;
import org.jetlinks.core.monitor.observation.ObservationSpec;
import org.jetlinks.core.monitor.observation.Observations;
import org.jetlinks.core.monitor.observation.RecorderProvider;
import org.jetlinks.core.monitor.observation.RecorderSelection;
import org.jetlinks.core.monitor.recorder.AbstractActionRecorder;
import org.jetlinks.core.monitor.recorder.ActionRecord;
import org.jetlinks.core.monitor.recorder.ActionRecorder;
import org.jetlinks.core.monitor.recorder.Recorder;
import org.jetlinks.core.things.ThingId;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.defaults.scope.InMemoryGlobalScope;
import org.jetlinks.supports.event.InternalEventBus;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.Disposables;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;
import reactor.util.context.Context;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

public class FunctionTaskObservationTest {

    @Test
    public void nonFunctionExecutorUsesTheSameObservationBoundary() {
        List<ActionRecord> records = new ArrayList<>();
        var context = executor(records, true, new AtomicBoolean()).getContext();
        var executor = new AbstractTaskExecutor(context) {
            public String getName() {
                return "Forward";
            }

            protected Disposable doStart() {
                return Disposables.disposed();
            }

            protected boolean observeExecution() {
                return true;
            }

            protected ObservationSpec<RuleData, RuleData> observationSpec() {
                return ObservationSpec.of("forward");
            }
        };
        var operation = executor.execute(RuleData.create("input"));
        assertTrue(records.isEmpty());
        StepVerifier.create(operation).verifyComplete();
        StepVerifier.create(operation).verifyComplete();
        assertEquals(2, records.size());
        assertEquals("forward", records.get(0).getAction());
        assertNotEquals(records.get(0).getId(), records.get(1).getId());
    }

    @Test
    public void providerOnlyReceivesSourceMetadataAtTheSingleExecutionBoundary() {
        List<ActionRecord> records = new ArrayList<>();
        Recorder recorder = new Recorder() {
            public <E> ActionRecorder<E> action(CharSequence action) {
                return new TestAction<>(action, null, records);
            }
        };
        ScheduleJob job = new ScheduleJob();
        job.setInstanceId("scene");
        job.setNodeId("evaluate");
        job.setExecutor("test");
        var context = new DefaultExecutionContext("worker", job, new InternalEventBus(),
            (condition, data) -> true, new InMemoryGlobalScope());
        var executor = new FunctionTaskExecutor("Evaluate", context) {
            protected boolean observeExecution() {
                return true;
            }
            protected ObservationSpec<RuleData, RuleData> observationSpec() {
                return ObservationSpec.<RuleData, RuleData>of("execute").onInput((input, metadata) ->
                    ObservationData.used(metadata, List.of(ThingId.of("metricResult", "value-1"))));
            }
            protected Publisher<RuleData> apply(RuleData input) {
                return Flux.just(input);
            }
        };
        RecorderProvider provider = (action, view) -> RecorderSelection.capture(recorder, "policy-1");
        StepVerifier.create(executor.execute(RuleData.create("candidate"))
            .contextWrite(Context.of(RecorderProvider.class, provider))).verifyComplete();
        assertEquals(1, records.size());
        assertEquals(List.of(ThingId.of("metricResult", "value-1")),
            records.get(0).getAttributes().get(ObservationData.INPUTS.getKey()));
    }

    @Test
    public void baseExecutorObservesAsyncChildrenResubscriptionAndCancellation() {
        List<ActionRecord> records = new ArrayList<>();
        AtomicBoolean pending = new AtomicBoolean();
        FunctionTaskExecutor executor = executor(records, true, pending);
        RuleData input = RuleData.create("alarm-1");
        var operation = executor.execute(input);
        assertTrue("Assembly must not create or start actions", records.isEmpty());
        StepVerifier.create(operation).verifyComplete();
        StepVerifier.create(operation).verifyComplete();
        assertEquals(4, records.size());
        List<ActionRecord> executions = records.stream().filter(record -> "execute".equals(record.getAction())).toList();
        assertEquals(2, executions.size());
        assertNotEquals(executions.get(0).getId(), executions.get(1).getId());
        for (ActionRecord execution : executions) {
            assertEquals("upstream", execution.getTags().get(ObservationKeys.PARENT_EXECUTION_ID.getKey()));
            ActionRecord child = records.stream().filter(record -> execution.getId().equals(record.getParentId()))
                .findFirst().orElseThrow();
            assertEquals("notify", child.getAction());
            assertEquals(execution.getTags().get(ObservationKeys.EXECUTION_ID.getKey()),
                child.getTags().get(ObservationKeys.PARENT_EXECUTION_ID.getKey()));
        }
        pending.set(true);
        StepVerifier.create(operation).thenCancel().verify();
        assertEquals(5, records.size());
        assertTrue(records.get(4).isCancel());
    }

    @Test
    public void legacyNodesKeepTheirOwnRecorderLifecycle() {
        List<ActionRecord> records = new ArrayList<>();
        StepVerifier.create(executor(records, false, new AtomicBoolean()).execute(RuleData.create("alarm-2")))
            .verifyComplete();
        assertEquals(1, records.size());
        assertEquals("legacy", records.get(0).getAction());
    }

    private static class TestAction<E> extends AbstractActionRecorder<E> {
        private final List<ActionRecord> records;

        TestAction(CharSequence action, String parentId, List<ActionRecord> records) {
            super(action, parentId);
            this.records = records;
        }

        protected void handle(ActionRecord record) {
            records.add(record);
        }

        public <T> ActionRecorder<T> child(CharSequence action) {
            return new TestAction<>(action, record.getId(), records);
        }
    }

    private FunctionTaskExecutor executor(List<ActionRecord> records, boolean observed, AtomicBoolean pending) {
        Recorder recorder = new Recorder() {
            public <E> ActionRecorder<E> action(CharSequence action) {
                return new TestAction<>(action, null, records);
            }
        };
        ScheduleJob job = new ScheduleJob();
        job.setInstanceId("scene");
        job.setNodeId("notify");
        job.setExecutor("test");
        DefaultExecutionContext context = new DefaultExecutionContext("worker", job, new InternalEventBus(),
            (condition, data) -> true, new InMemoryGlobalScope()) {
            public Recorder recorder() {
                return recorder;
            }
        };
        return new FunctionTaskExecutor("Notify", context) {
            protected boolean observeExecution() {
                return observed;
            }
            protected Context prepareContext(Context context, RuleData input) {
                return context.put(ObservationReference.class, new ObservationReference("upstream"));
            }
            protected Publisher<RuleData> apply(RuleData input) {
                if (pending.get()) {
                    return Flux.never();
                }
                if (!observed) {
                    return Flux.from(context.<RuleData>recordAction("legacy").apply(Flux.just(input)));
                }
                return Observations.observeFlux("notify", () -> Flux.just(input))
                    .subscribeOn(Schedulers.parallel());
            }
        };
    }
}
