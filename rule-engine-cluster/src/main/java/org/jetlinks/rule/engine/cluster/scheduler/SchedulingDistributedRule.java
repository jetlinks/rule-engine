package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.core.cluster.ServerNode;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.cluster.SchedulingRule;
import org.jetlinks.rule.engine.api.events.EventPublisher;
import org.jetlinks.rule.engine.api.events.RuleInstanceStateChangedEvent;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.api.cluster.ServerNodeHelper;
import org.jetlinks.rule.engine.api.cluster.WorkerNodeSelector;
import org.jetlinks.rule.engine.cluster.events.ClusterRuleErrorEvent;
import org.jetlinks.rule.engine.cluster.events.ClusterRuleSuccessEvent;
import org.jetlinks.rule.engine.cluster.events.RuleStartEvent;
import org.jetlinks.rule.engine.cluster.events.RuleStopEvent;
import org.jetlinks.rule.engine.cluster.message.EventConfig;
import org.jetlinks.rule.engine.cluster.message.InputConfig;
import org.jetlinks.rule.engine.cluster.message.OutputConfig;
import org.jetlinks.rule.engine.cluster.message.StartRuleNodeRequest;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

//分布式规则
@Slf4j
class SchedulingDistributedRule extends AbstractSchedulingRule {

    private org.jetlinks.core.cluster.ClusterManager clusterManager;

    private EventPublisher eventPublisher;

    private WorkerNodeSelector nodeSelector;

    @Getter
    private final ClusterRuleInstanceContext context;

    private List<StartRuleNodeRequest> requests;

    private Set<ServerNode> allRunningNode = new HashSet<>();

    private Rule rule;

    private Map<String, List<ServerNode>> nodeRunnerInfo = new HashMap<>();

    private AtomicReference<RuleInstanceState> state = new AtomicReference<>(RuleInstanceState.initializing);

    @Override
    public RuleInstanceState getState() {
        return state.get();
    }

    @Override
    protected void setState(RuleInstanceState state) {
        this.state.set(state);
    }


    public SchedulingDistributedRule(Rule rule, String id, org.jetlinks.core.cluster.ClusterManager clusterManager, EventPublisher eventPublisher, WorkerNodeSelector selector) {
        this.clusterManager = clusterManager;
        this.eventPublisher = eventPublisher;
        this.nodeSelector = selector;

        this.context = new ClusterRuleInstanceContext();
        this.context.setStateSupplier(this::getState);

        requests = new ArrayList<>();
        context.setClusterManager(clusterManager);
        context.setId(id);
        context.setOnStop(() -> {
            //停止规则
            return doStop(clusterManager.getHaManager()
                    .getAllNode()
                    .stream()
                    .filter(node -> node.hasTag("rule-worker"))
                    .collect(Collectors.toList()))
                    .doFinally(nil -> eventPublisher.publishEvent(RuleStopEvent.of(id, rule.getId())));
        });
        context.setOnStart(() -> {
            //启动规则
            return start()
                    .doFinally(nil -> eventPublisher.publishEvent(RuleStartEvent.of(id, rule.getId())))
                    .then();
        });

        context.setQueueGetter(nodeId ->
                rule.getModel()
                        .getNode(nodeId)
                        .map(model -> getDataQueueName(id, model))
                        .map(clusterManager::<RuleData>getQueue)
                        .orElseThrow(() -> new NullPointerException("节点[" + nodeId + "]不存在")));

        this.rule = rule;
        prepare(false);
    }

    private String getDataQueueName(String id, RuleNodeModel model) {
        return "data:" + id + ":node:" + model.getId();
    }

    private void prepare(boolean checkWorker) {
        requests.clear();
        allRunningNode.clear();

        //分配任务
        for (RuleNodeModel node : rule.getModel().getNodes()) {
            if (node.isEnd()) {
                context.setSyncReturnNodeId(node.getId());
            }
            prepare(node);

            SchedulingRule schedulingRule = Optional.ofNullable(node.getSchedulingRule())
                    .orElseGet(rule.getModel()::getSchedulingRule);

            //选择执行节点
            List<ServerNode> nodes = nodeSelector.select(schedulingRule, clusterManager.getHaManager().getAllNode());
            if (checkWorker && CollectionUtils.isEmpty(nodes)) {
                log.warn("没有可以执行任务[{}-{}]的worker", getContext().getId(), node.getName());
            }
            allRunningNode.addAll(nodes);
            nodeRunnerInfo.put(node.getId(), nodes);
        }
    }

    private void prepare() {
        prepare(true);
    }

    private List<ServerNode> getNodeRunnerWorker(String nodeId) {
        return nodeRunnerInfo.getOrDefault(nodeId, Collections.emptyList());
    }

    public Mono<Boolean> init() {
        prepare();

        return Flux.concat(requests
                .stream()
                .flatMap(request -> getNodeRunnerWorker(request.getNodeId())
                        .stream()
                        .map(nodeInfo -> this.<Boolean>sendNotify(NotifyType.init, nodeInfo, request, request)))
                .collect(Collectors.toList()))
                .all(r -> r);
    }

    @SneakyThrows
    public Mono<Boolean> tryResume(String workerId) {
        //重新处理节点
        prepare();

        return Flux.concat(requests
                .stream()
                .flatMap(request -> getNodeRunnerWorker(request.getNodeId())
                        .stream()
                        .map(nodeInfo -> this.<Boolean>sendNotify(NotifyType.start, nodeInfo, request, request))).collect(Collectors.toList()))
                .all(r -> r);

    }

    private Mono<Void> doStop(Collection<ServerNode> nodeList) {
        changeState(RuleInstanceState.stopping);

        return Flux.concat(nodeList
                .stream()
                .map(node -> clusterManager
                        .getNotifier()
                        .sendNotifyAndReceive(node.getId(), "rule:stop", Mono.just(context.getId()))).collect(Collectors.toList()))
                .doOnError(err -> {
                    changeState(RuleInstanceState.stopFailed);
                })
                .doOnComplete(() -> {
                    changeState(RuleInstanceState.stopped);
                })
                .then();

    }

    private Mono<Boolean> doStart(ServerNode node) {
        return clusterManager
                .getNotifier()
                .sendNotifyAndReceive(node.getId(), "rule:start", Mono.just(context.getId()));
    }

    public Mono<Boolean> stop() {
       ;

        return context.stop().then(Mono.just(true)) ;
    }

    @SneakyThrows
    public Mono<Boolean> start() {


        return init()
                .flatMapMany(r -> Flux.fromIterable(clusterManager.getHaManager()
                        .getAllNode())
                        .filter(ServerNodeHelper::isWorker)
                        .flatMap(this::doStart))
                .doOnSubscribe(sb -> {
                    log.info("start rule {}", rule.getId());
                    changeState(RuleInstanceState.starting);
                })
                .doOnError(error -> {
                    log.error("启动规则失败", error);
                    changeState(RuleInstanceState.startFailed);
                    stop(); //启动失败,停止任务
                })
                .all(r -> r)
                .doOnSuccess((r) -> {
                    changeState(RuleInstanceState.started);
                });
    }

    private void prepare(RuleNodeModel model) {
        StartRuleNodeRequest request = new StartRuleNodeRequest();
        String id = context.getId();
        request.setSchedulerNodeId(clusterManager.getCurrentServerId());
        request.setInstanceId(context.getId());
        request.setNodeId(model.getId());
        request.setRuleId(rule.getId());
        request.setNodeConfig(model.createConfiguration());
        //分布式
        request.setDistributed(rule.getModel().getRunMode() == RunMode.DISTRIBUTED);

        request.getLogContext().put("ruleId", rule.getId());
        request.getLogContext().put("instanceId", context.getId());
        request.getLogContext().put("nodeId", model.getId());
        //event
        for (RuleLink eventLink : model.getEvents()) {
            RuleNodeModel event = eventLink.getTarget();
            EventConfig inputConfig = new EventConfig();
            inputConfig.setEvent(eventLink.getType());
            inputConfig.setCondition(eventLink.getCondition());
            inputConfig.setQueue(getDataQueueName(id, event));
            request.getEventQueue().add(inputConfig);
        }
        if (model.isStart()) {
            context.setInputQueue(clusterManager.getQueue("data:" + id + ":input"));
            request.getInputQueue().add(new InputConfig("data:" + id + ":input"));
        }
        if (model.getInputs().isEmpty()) {
            request.getInputQueue().add(new InputConfig(getDataQueueName(id, model)));
        }
        //input
        for (RuleLink inputLink : model.getInputs()) {
            RuleNodeModel source = inputLink.getTarget();
            InputConfig inputConfig = new InputConfig(getDataQueueName(id, source));
            request.getInputQueue().add(inputConfig);
        }
        //output
        for (RuleLink outputLink : model.getOutputs()) {
            RuleNodeModel target = outputLink.getTarget();
            OutputConfig outputConfig = new OutputConfig();
            outputConfig.setQueue(getDataQueueName(id, target));
            outputConfig.setCondition(outputLink.getCondition());
            request.getOutputQueue().add(outputConfig);
        }
        requests.add(request);
    }

    void changeState(RuleInstanceState state) {
        RuleInstanceState old = getState();
        this.state.set(state);
        eventPublisher.publishEvent(RuleInstanceStateChangedEvent.of(clusterManager.getCurrentServerId(), getContext().getId(), old, state));
    }

    @AllArgsConstructor
    enum NotifyType {
        init("rule:node:init", "init"),
        start("rule:start", "start"),
        stop("rule:stop", "stop"),
        ;
        private String address;

        private String type;
    }


    private <T> Mono<T> sendNotify(NotifyType type, ServerNode nodeInfo, StartRuleNodeRequest request, Object notifyData) {
        return clusterManager.getNotifier()
                .<T>sendNotifyAndReceive(nodeInfo.getId(), type.address, Mono.just(notifyData))
                .doOnError((error) -> {
                    eventPublisher.publishEvent(ClusterRuleErrorEvent.of(type.type, request.getRuleId(), request.getInstanceId(), nodeInfo.getId()));
                    log.warn("{} rule node [{}].[{}] error", type.type, request.getRuleId(), request.getNodeId(), error);

                }).doOnSuccess(r -> {
                    eventPublisher.publishEvent(ClusterRuleSuccessEvent.of(type.type, request.getRuleId(), request.getInstanceId(), nodeInfo.getId()));
                });
    }


}
