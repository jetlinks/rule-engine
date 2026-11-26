package org.jetlinks.rule.engine.cluster.worker;

import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.cluster.ClusterQueue;
import org.jetlinks.core.monitor.Monitor;
import org.jetlinks.rule.engine.api.RuleConstants;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.defaults.AbstractExecutionContext;
import org.jetlinks.rule.engine.defaults.EventBusOutput;
import org.jetlinks.rule.engine.defaults.scope.InMemoryGlobalScope;
import org.jetlinks.supports.event.InternalEventBus;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.publisher.TestPublisher;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class QueueInputObservationTest {
    @Test
    public void queueCallbacksKeepRejectionErrorsAndCancellationOnTheOriginalInputPath() {
        var source = TestPublisher.<RuleData>create();
        Object queue = Proxy.newProxyInstance(ClusterQueue.class.getClassLoader(), new Class<?>[]{ClusterQueue.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("subscribe")) return source.flux();
                throw new UnsupportedOperationException(method.getName());
            });
        var manager = (ClusterManager) Proxy.newProxyInstance(ClusterManager.class.getClassLoader(), new Class<?>[]{ClusterManager.class},
            (proxy, method, arguments) -> {
                if (method.getName().equals("getQueue")) {
                    assertEquals(RuleConstants.Topics.input("rule", "node"), arguments[0]);
                    return queue;
                }
                throw new UnsupportedOperationException(method.getName());
            });
        var job = new ScheduleJob(); job.setInstanceId("rule"); job.setNodeId("node");
        var bus = new InternalEventBus();
        var context = new AbstractExecutionContext(job, bus,
            ignored -> Monitor.noop(), ignored -> new QueueInput("rule", "node", manager),
            ignored -> new EventBusOutput("rule", bus, List.of(), (condition, data) -> true),
            ignored -> Map.of(), ignored -> new InMemoryGlobalScope()) {};
        var subscription = context.getInput().accept(data -> {
            if ("throw".equals(data.getData())) throw new IllegalStateException("callback");
            return Mono.just(!"reject".equals(data.getData()));
        });
        try {
            source.assertSubscribers().assertMinRequested(1);
            source.next(RuleData.create("reject"), RuleData.create("throw"), RuleData.create("ok"));
            assertEquals(3, context.getExecutionMetrics().getExecutionCount());
            assertEquals(1, context.getExecutionMetrics().getErrorCount());
        } finally { subscription.dispose(); }
        source.assertCancelled();
    }
}
