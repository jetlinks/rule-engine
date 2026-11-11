package org.jetlinks.rule.engine.cluster.scheduler;

import io.scalecube.cluster.ClusterConfig;
import io.scalecube.services.transport.rsocket.RSocketClientTransportFactory;
import io.scalecube.services.transport.rsocket.RSocketServerTransportFactory;
import io.scalecube.services.transport.rsocket.RSocketServiceTransport;
import io.scalecube.transport.netty.tcp.TcpTransportFactory;
import org.jetlinks.core.rpc.ServiceEvent;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;
import org.jetlinks.rule.engine.api.task.ExecutionContext;
import org.jetlinks.rule.engine.api.task.Task;
import org.jetlinks.rule.engine.api.task.TaskExecutor;
import org.jetlinks.rule.engine.api.task.TaskExecutorProvider;
import org.jetlinks.rule.engine.cluster.task.RemoteTask;
import org.jetlinks.rule.engine.defaults.FunctionTaskExecutor;
import org.jetlinks.rule.engine.defaults.LocalScheduler;
import org.jetlinks.rule.engine.defaults.LocalWorker;
import org.jetlinks.supports.event.InternalEventBus;
import org.jetlinks.supports.scalecube.ExtendedClusterImpl;
import org.jetlinks.supports.scalecube.rpc.ScalecubeRpcManager;
import org.junit.Test;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;

import static org.junit.Assert.*;

public class TaskDumpMetricsRpcTest {
    @Test(timeout = 60000)
    public void existingSchedulerRpcReturnsMetricsAndTaskRecreationResetsThem() {
        var cluster1 = new ExtendedClusterImpl(new ClusterConfig()
            .transport(t -> t.transportFactory(new TcpTransportFactory())));
        cluster1.startAwait();
        var rpc1 = rpc(cluster1);
        var cluster2 = new ExtendedClusterImpl(new ClusterConfig()
            .membership(m -> m.seedMembers(cluster1.address()))
            .transport(t -> t.transportFactory(new TcpTransportFactory())));
        cluster2.startAwait();
        var rpc2 = rpc(cluster2);
        var scheduler = new LocalScheduler("scheduler");
        try {
            var worker = new LocalWorker("worker", "测试工作器", new InternalEventBus(), (condition, data) -> true);
            worker.addExecutor(new TaskExecutorProvider() {
                public String getExecutor() { return "test"; }
                public Mono<TaskExecutor> createTask(ExecutionContext context) {
                    return Mono.just(new FunctionTaskExecutor("test", context) {
                        protected Publisher<RuleData> apply(RuleData input) {
                            return "fail".equals(input.getData())
                                ? Mono.error(new IllegalStateException("failure")) : Mono.just(input);
                        }
                    });
                }
            });
            scheduler.addWorker(worker);
            var job = new ScheduleJob();
            job.setInstanceId("scene");
            job.setNodeId("scene");
            job.setExecutor("test");
            scheduler.schedule(job).blockLast();
            rpc2.registerService("scheduler", new SchedulerRpcServiceImpl(scheduler));
            // 用服务发现作为就绪边界，不使用 sleep 猜测集群建立时间。
            rpc1.listen(SchedulerRpcService.class)
                .filter(event -> event.getType() == ServiceEvent.Type.added
                    && event.getServerNodeId().equals(rpc2.currentServerId()))
                .map(ServiceEvent::getServerNodeId)
                .mergeWith(rpc1.getServices(SchedulerRpcService.class)
                    .map(service -> service.serverNodeId()).filter(rpc2.currentServerId()::equals))
                .next().timeout(Duration.ofSeconds(20)).block();
            var service = rpc1.getService(rpc2.currentServerId(), "scheduler", SchedulerRpcService.class)
                .block(Duration.ofSeconds(5));
            assertNotNull(service);
            var remote = new ClusterRemoteScheduler("scheduler", service);
            var task = remote.getSchedulingTask("scene").single().block();
            assertTrue(task instanceof RemoteTask);
            task.execute(RuleData.create("ok")).block();
            task.execute(RuleData.create("fail")).block();
            StepVerifier.create(remote.getSchedulingTask("scene").flatMap(Task::dump))
                .assertNext(snapshot -> {
                    assertEquals("scene", snapshot.getJob().getNodeId());
                    assertEquals(2, snapshot.getExecutionMetrics().getExecutionCount());
                    assertEquals(1, snapshot.getExecutionMetrics().getErrorCount());
                    assertTrue(snapshot.getExecutionMetrics().getLastExecutionTime() > 0);
                }).verifyComplete();
            remote.shutdown("scene").block();
            StepVerifier.create(remote.getSchedulingTask("scene")).verifyComplete();
            scheduler.schedule(job).blockLast();
            StepVerifier.create(remote.getSchedulingTask("scene").flatMap(Task::dump))
                .assertNext(snapshot -> {
                    assertEquals(0, snapshot.getExecutionMetrics().getExecutionCount());
                    assertEquals(0, snapshot.getExecutionMetrics().getLastExecutionTime());
                }).verifyComplete();
        } finally {
            scheduler.dispose();
            rpc2.stopAwait();
            rpc1.stopAwait();
            cluster2.shutdown();
            cluster1.shutdown();
        }
    }

    private static ScalecubeRpcManager rpc(ExtendedClusterImpl cluster) {
        var rpc = new ScalecubeRpcManager(cluster, () -> new RSocketServiceTransport()
            .serverTransportFactory(RSocketServerTransportFactory.tcp())
            .clientTransportFactory(RSocketClientTransportFactory.tcp()));
        rpc.startAwait();
        return rpc;
    }
}
