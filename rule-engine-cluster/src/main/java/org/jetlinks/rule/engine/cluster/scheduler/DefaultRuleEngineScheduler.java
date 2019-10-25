package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.dict.EnumDict;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.cluster.ClusterTopic;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleEngine;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.cluster.ServerNodeHelper;
import org.jetlinks.rule.engine.api.cluster.WorkerNodeSelector;
import org.jetlinks.rule.engine.api.events.DefaultEventPubSub;
import org.jetlinks.rule.engine.api.events.EventPublisher;
import org.jetlinks.rule.engine.api.events.EventSubscriber;
import org.jetlinks.rule.engine.api.events.RuleInstanceStateChangedEvent;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.cluster.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.cluster.persistent.repository.RuleInstanceRepository;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static org.jetlinks.rule.engine.api.RuleInstanceState.*;

@Slf4j
@SuppressWarnings("all")
public class DefaultRuleEngineScheduler implements RuleEngineScheduler, RuleEngine {

    @Getter
    @Setter
    private ClusterManager clusterManager;

    @Getter
    @Setter
    private WorkerNodeSelector nodeSelector;

    @Getter
    @Setter
    private RuleInstanceRepository instanceRepository;

    @Getter
    @Setter
    private RuleEngineModelParser modelParser;

    @Setter
    @Getter
    private EventPublisher eventPublisher = DefaultEventPubSub.GLOBAL;

    @Getter
    @Setter
    private EventSubscriber eventSubscriber = DefaultEventPubSub.GLOBAL;

    protected Map<String, AbstractSchedulingRule> contextCache = new ConcurrentHashMap<>();

    public Mono<Void> start() {
        return loadAllRule()
                .doOnSuccess(nil -> {
                    long schedulerSize = clusterManager.getHaManager().getAllNode()
                            .stream()
                            .filter(ServerNodeHelper::isScheduler)
                            .count();

                    //集群第一个调度节点恢复?
                    if (schedulerSize <= 1) {
                        contextCache.values()
                                .stream()
                                .filter((schedulingRule) -> EnumDict.in(schedulingRule.getState(), starting, started, startFailed))
                                .forEach(schedulingRule -> {
                                    schedulingRule.start()
                                            .doOnError(error -> {
                                                log.error("start [{}] rule error", schedulingRule.getContext().getId(), error);
                                            })
                                            .subscribe();
                                });
                    }

                    ClusterTopic<RuleInstanceStateChangedEvent> topic = clusterManager.getTopic("rule-instance-state-changed");

                    topic.subscribe()
                            .subscribe(event -> {
                                if (clusterManager.getCurrentServerId().equals(event.getSchedulerId())) {
                                    return;
                                }
                                log.debug("rule instance [{}] state changed [{}]=>[{}],scheduler:[{}]"
                                        , event.getInstanceId(), event.getBefore(), event.getAfter(), event.getSchedulerId());
                                //同步其他节点发来的状态信息
                                getOrCreateSchedulingRule(event.getInstanceId())
                                        .subscribe(rule -> rule.setState(event.getAfter()));

                            });

                    //当前节点状态发生了变化,通知其他节点
                    eventSubscriber
                            .subscribe(RuleInstanceStateChangedEvent.class, event -> {
                                topic.publish(Mono.just(event)).subscribe();
                                instanceRepository.changeState(event.getInstanceId(), event.getAfter());
                            });

                    //有节点上线了,如果是worker节点,则尝试恢复该节点上的任务
                    clusterManager
                            .getHaManager()
                            .subscribeServerOnline()
                            .subscribe(node -> {
                                if (ServerNodeHelper.isWorker(node)) {
                                    contextCache.values()
                                            .stream()
                                            .filter((schedulingRule) -> EnumDict.in(schedulingRule.getState(), starting, started, startFailed))
                                            .forEach(schedulingRule -> {
                                                schedulingRule.tryResume(node.getId())
                                                        .doOnError(error -> {
                                                            log.error("resume [{}] rule error", node.getId(), error);
                                                        });
                                            });
                                }
                            });

                    clusterManager.getNotifier().<RuleData>handleNotify("execute-complete").subscribe(data -> {
                        data.getAttribute("instanceId")
                                .map(String::valueOf)
                                .map(contextCache::get)
                                .map(SchedulingRule::getContext)
                                .map(ClusterRuleInstanceContext.class::cast)
                                .ifPresent(context -> context.executeComplete(data));

                    });
                    clusterManager.getNotifier()
                            .<RuleData>handleNotify("execute-result").subscribe(data -> {

                        data.getAttribute("instanceId")
                                .map(String::valueOf)
                                .map(contextCache::get)
                                .map(SchedulingRule::getContext)
                                .map(ClusterRuleInstanceContext.class::cast)
                                .ifPresent(context -> context.executeResult(data));

                    });
                });
    }

    protected Mono<Void> loadAllRule() {
        return instanceRepository.findAll()
                .doOnNext(instance -> contextCache.put(instance.getId(), initRuleInstance(instance)))
                .then();

    }

    protected AbstractSchedulingRule initRuleInstance(RuleInstancePersistent persistent) {
        Rule rule = persistent.toRule(modelParser);
        AbstractSchedulingRule scheduling = createRunningRule(rule, persistent.getId());
        scheduling.setState(persistent.getState());
        return scheduling;
    }

    @Override
    public Mono<RuleInstanceContext> startRule(Rule rule) {
        AbstractSchedulingRule schedulingRule = createRunningRule(rule, rule.getId());


        RuleInstanceContext context = schedulingRule.getContext();
        contextCache.put(context.getId(), schedulingRule);

        return context.start()
                .then(Mono.just(context));
    }

    @Override
    public Mono<RuleInstanceContext> getInstance(String id) {
        return getOrCreateSchedulingRule(id)
                .map(AbstractSchedulingRule::getContext);
    }

    private Mono<AbstractSchedulingRule> createRunningRule(String instanceId) {

        return instanceRepository
                .findInstanceById(instanceId)
                .map(this::initRuleInstance);
    }

    public Mono<AbstractSchedulingRule> getOrCreateSchedulingRule(String id) {
        return Mono.defer(() -> {
            if (contextCache.containsKey(id)) {
                return Mono.justOrEmpty(contextCache.get(id));
            }
            synchronized (this) {
                return this.createRunningRule(id)
                        .doOnNext(r -> contextCache.putIfAbsent(id, r));
            }
        });

    }

    private AbstractSchedulingRule createRunningRule(Rule rule, String instanceId) {
        return new SchedulingDistributedRule(rule, instanceId, clusterManager, eventPublisher, nodeSelector);
    }

    @Override
    public Optional<SchedulingRule> getSchedulingRule(String instanceId) {
        return Optional.ofNullable(contextCache.get(instanceId));
    }

    @Override
    public SchedulingRule schedule(String instanceId, Rule rule) {

        return contextCache.computeIfAbsent(instanceId, _id -> {
            return createRunningRule(rule, _id);
        });
    }
}
