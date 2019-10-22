package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.dict.EnumDict;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleEngine;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.Topic;
import org.jetlinks.rule.engine.api.cluster.WorkerNodeSelector;
import org.jetlinks.rule.engine.api.cluster.scheduler.RuleEngineScheduler;
import org.jetlinks.rule.engine.api.cluster.scheduler.SchedulingRule;
import org.jetlinks.rule.engine.api.events.DefaultEventPubSub;
import org.jetlinks.rule.engine.api.events.EventPublisher;
import org.jetlinks.rule.engine.api.events.EventSubscriber;
import org.jetlinks.rule.engine.api.events.RuleInstanceStateChangedEvent;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleInstanceRepository;
import org.jetlinks.rule.engine.api.persistent.repository.RuleRepository;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

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
    private RuleRepository ruleRepository;

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

    public void start() {

        long schedulerSize = clusterManager.getAllAliveNode()
                .stream()
                .filter(NodeInfo::isScheduler)
                .count();

        //加载所有任务
        loadAllRule();

        //集群第一个调度节点恢复?
        if (schedulerSize <= 1) {
            contextCache.values()
                    .stream()
                    .filter((schedulingRule) -> EnumDict.in(schedulingRule.getState(), starting, started, startFailed))
                    .forEach(schedulingRule -> {
                        schedulingRule.start()
                                .whenComplete((nil, error) -> {
                                    if (error != null) {
                                        log.error("start [{}] rule error", schedulingRule.getContext().getId(), error);
                                    }
                                });
                    });
        }

        Topic<RuleInstanceStateChangedEvent> topic = clusterManager.getTopic(RuleInstanceStateChangedEvent.class, "rule-instance-state-changed");

        topic.subscribe()
                .subscribe(event -> {
                    if (clusterManager.getCurrentNode().getId().equals(event.getSchedulerId())) {
                        return;
                    }
                    log.debug("rule instance [{}] state changed [{}]=>[{}],scheduler:[{}]"
                            , event.getInstanceId(), event.getBefore(), event.getAfter(), event.getSchedulerId());
                    //同步其他节点发来的状态信息
                    getOrCreateSchedulingRule(event.getInstanceId()).setState(event.getAfter());

                });

        //当前节点状态发生了变化,通知其他节点
        eventSubscriber
                .subscribe(RuleInstanceStateChangedEvent.class, event -> {
                    topic.publish(event);
                    instanceRepository.changeState(event.getInstanceId(), event.getAfter());
                });

        //有节点上线了,如果是worker节点,则要恢复该节点上的任务
        clusterManager
                .getHaManager()
                .onNodeJoin(node -> {
                    if (node.isWorker()) {
                        // FIXME: 2019-07-17
                        //  如果有多个调度器,会同时发送此通知;
                        //  虽然worker做了幂等,但是任务数量较多时,会不会有问题?

                        contextCache.values().stream()
                                .filter((schedulingRule) -> EnumDict.in(schedulingRule.getState(), starting, started, startFailed))
                                .forEach(schedulingRule -> {
                                    schedulingRule.tryResume(node.getId())
                                            .whenComplete((nil, error) -> {
                                                if (error != null) {
                                                    log.error("resume [{}] rule error", node.getId(), error);
                                                }
                                            });
                                });
                    }
                });

        clusterManager.getHaManager()
                .<RuleData, Object>onNotify("execute-complete", data -> {
                    data.getAttribute("instanceId")
                            .map(String::valueOf)
                            .map(contextCache::get)
                            .map(SchedulingRule::getContext)
                            .map(ClusterRuleInstanceContext.class::cast)
                            .ifPresent(context -> context.executeComplete(data));

                    return null;
                });
        clusterManager.getHaManager()
                .<RuleData, Object>onNotify("execute-result", data -> {

                    data.getAttribute("instanceId")
                            .map(String::valueOf)
                            .map(contextCache::get)
                            .map(SchedulingRule::getContext)
                            .map(ClusterRuleInstanceContext.class::cast)
                            .ifPresent(context -> context.executeResult(data));

                    return null;
                });
    }

    protected void loadAllRule() {
        List<RuleInstancePersistent> allInstance = instanceRepository.findAll();
        Map<String, RulePersistent> allRule = ruleRepository
                .findRuleByIdList(allInstance.stream().map(RuleInstancePersistent::getRuleId).collect(Collectors.toList()))
                .stream()
                .collect(Collectors.toMap(RulePersistent::getId, Function.identity()));

        for (RuleInstancePersistent instance : allInstance) {
            contextCache.put(instance.getId(), initRuleInstance(allRule.get(instance.getRuleId()), instance));
        }
    }

    protected AbstractSchedulingRule initRuleInstance(RulePersistent rulePersistent, RuleInstancePersistent instancePersistent) {

        Rule rule = rulePersistent.toRule(modelParser);
        AbstractSchedulingRule scheduling = createRunningRule(rule, instancePersistent.getId());
        scheduling.setState(instancePersistent.getState());
        return scheduling;
    }

    @Override
    public RuleInstanceContext startRule(Rule rule) {
        AbstractSchedulingRule schedulingRule = createRunningRule(rule, rule.getId());

        instanceRepository.saveInstance(schedulingRule.toPersistent());

        schedulingRule.init();

        RuleInstanceContext context = schedulingRule.getContext();
        context.start();

        contextCache.put(context.getId(), schedulingRule);
        return context;
    }

    @Override
    public RuleInstanceContext getInstance(String id) {
        return getOrCreateSchedulingRule(id).getContext();
    }

    private AbstractSchedulingRule createRunningRule(String instanceId) {
        RuleInstancePersistent instance = instanceRepository
                .findInstanceById(instanceId)
                .orElseThrow(() -> new NullPointerException("规则实例[" + instanceId + "]不存在"));
        RulePersistent rule = ruleRepository.findRuleById(instance.getRuleId())
                .orElseThrow(() -> new NullPointerException("规则[" + instance.getRuleId() + "]不存在"));
        return initRuleInstance(rule, instance);
    }

    public AbstractSchedulingRule getOrCreateSchedulingRule(String id) {
        return contextCache
                .computeIfAbsent(id, this::createRunningRule);
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
