package org.jetlinks.rule.engine.executor.supports;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterCache;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import org.springframework.scheduling.support.CronSequenceGenerator;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

@Slf4j
public class TimerStrategy extends CommonExecutableRuleNodeFactoryStrategy<TimerStrategy.Configuration> {

    private ClusterManager clusterManager;

    private ClusterCache<String, TimerInfo> workerInfo;

    private Map<String, TimerJob> jobs = new ConcurrentHashMap<>();

    @Override
    public String getSupportType() {
        return "timer";
    }

    public TimerStrategy(ClusterManager clusterManager) {
        this.clusterManager = clusterManager;
        this.workerInfo = clusterManager.getCache("_rule-engine-timer-worker");

        this.clusterManager
                .getHaManager()
                .subscribeServerOffline()
                .subscribe(server -> {
                    //其他节点下线时,尝试竞争这些节点上的任务
                    workerInfo.entries()
                            .map(Map.Entry::getKey)
                            .flatMap(id -> Mono.justOrEmpty(jobs.get(id)))
                            .flatMap(job -> workerInfo.put(job.id, job.timerInfo.current(clusterManager.getCurrentServerId())).thenReturn(job))
                            .subscribe(TimerJob::start);
                });

    }

    @Override
    public Function<RuleData, Publisher<Object>> createExecutor(ExecutionContext context, Configuration config) {
        return Mono::just;
    }


    @Override
    protected void onStarted(ExecutionContext context, Configuration configuration) {
        super.onStarted(context, configuration);
        String id = context.getInstanceId() + ":" + context.getNodeId();
        context.onStop(() -> {
            TimerJob job = jobs.remove(id);
            if (null != job) {
                context.logger().info("cancel timer");
                job.cancel();
            }
        });
        //竞争任务
        workerInfo.get(id)
                .switchIfEmpty(Mono.just(TimerInfo.builder().id(id).currentWorker(clusterManager.getCurrentServerId()).firstWorker(clusterManager.getCurrentServerId()).build()))
                .flatMap(info -> workerInfo.putIfAbsent(id, info).then(workerInfo.get(id)))
                .map(timerInfo -> new TimerJob(configuration, context, timerInfo))
                .doOnError(err -> context.logger().error("start timer error", err))
                .subscribe(job -> {
                    TimerJob old = jobs.put(job.id, job);
                    if (old != null) {
                        old.cancel();
                    }
                    if (job.timerInfo.getFirstWorker().equals(clusterManager.getCurrentServerId())) {
                        job.timerInfo.setCurrentWorker(clusterManager.getCurrentServerId());
                        workerInfo.put(job.id, job.timerInfo).subscribe(r -> job.start());
                    } else if (job.timerInfo.getCurrentWorker().equals(clusterManager.getCurrentServerId())) {
                        job.start();
                    }
                });
    }

    @AllArgsConstructor
    class TimerJob {
        private String id;
        private Configuration configuration;
        private ExecutionContext context;
        private volatile boolean running;
        private TimerInfo timerInfo;

        TimerJob(Configuration configuration,
                 ExecutionContext context,
                 TimerInfo timerInfo) {
            this.configuration = configuration;
            this.context = context;
            this.id = context.getInstanceId() + ":" + context.getNodeId();
            this.timerInfo = timerInfo;
        }


        void start() {
            running = true;
            doStart();
        }

        void doStart() {
            if (!running) {
                return;
            }
            running = true;
            Mono.delay(Duration.ofMillis(configuration.nextMillis()))
                    .subscribe(t -> execute(this::start));
        }

        void execute(Runnable runnable) {
            if (!running) {
                return;
            }
            workerInfo.get(id)
                    .filter(s -> {
                        if (clusterManager.getCurrentServerId().equals(s.getCurrentWorker())) {
                            return running;
                        }
                        context.logger().debug("timer running another server:[{}]", s);
                        cancel();
                        return false;
                    })
                    .doOnNext(str -> context.logger().debug("execute timer"))
                    .flatMap((id) -> context.getOutput()
                            .write(Mono.just(RuleData.create(System.currentTimeMillis()))))
                    .doOnError(err -> context.logger().error("fire timer error", err))
                    .doFinally(s -> runnable.run())
                    .subscribe();

        }

        void cancel() {
            running = false;
        }
    }

    public static class Configuration implements RuleNodeConfig {
        @Getter
        @Setter
        private String cron;

        private volatile CronSequenceGenerator generator;

        @Override
        public NodeType getNodeType() {
            return NodeType.PEEK;
        }

        @Override
        public void setNodeType(NodeType nodeType) {

        }

        public void init() {
            generator = new CronSequenceGenerator(cron);
        }

        @Override
        public void validate() {
            init();
        }

        public long nextMillis() {
            return Math.max(100, generator.next(new Date()).getTime() - System.currentTimeMillis());
        }

    }

}
