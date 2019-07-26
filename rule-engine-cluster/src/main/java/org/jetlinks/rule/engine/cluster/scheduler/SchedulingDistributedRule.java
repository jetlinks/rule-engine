package org.jetlinks.rule.engine.cluster.scheduler;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hswebframework.web.NotFoundException;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.cluster.*;
import org.jetlinks.rule.engine.api.events.EventPublisher;
import org.jetlinks.rule.engine.api.events.RuleInstanceStateChangedEvent;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.cluster.events.ClusterRuleErrorEvent;
import org.jetlinks.rule.engine.cluster.events.ClusterRuleSuccessEvent;
import org.jetlinks.rule.engine.cluster.events.RuleStartEvent;
import org.jetlinks.rule.engine.cluster.events.RuleStopEvent;
import org.jetlinks.rule.engine.cluster.message.EventConfig;
import org.jetlinks.rule.engine.cluster.message.InputConfig;
import org.jetlinks.rule.engine.cluster.message.OutputConfig;
import org.jetlinks.rule.engine.cluster.message.StartRuleNodeRequest;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.CompletableFuture.completedFuture;

//分布式规则
@Slf4j
class SchedulingDistributedRule extends AbstractSchedulingRule {

    private ClusterManager clusterManager;

    private EventPublisher eventPublisher;

    private WorkerNodeSelector nodeSelector;


    @Getter
    private final ClusterRuleInstanceContext context;

    private List<StartRuleNodeRequest> requests;

    private Set<NodeInfo> allRunningNode = new HashSet<>();

    private Rule rule;

    private Map<String, List<NodeInfo>> nodeRunnerInfo = new HashMap<>();

    private AtomicReference<RuleInstanceState> state = new AtomicReference<>(RuleInstanceState.initializing);


    @Override
    public RuleInstanceState getState() {
        return state.get();
    }

    @Override
    protected void setState(RuleInstanceState state) {
        this.state.set(state);
    }

    public RuleInstancePersistent toPersistent() {
        RuleInstancePersistent persistent = new RuleInstancePersistent();
        persistent.setId(context.getId());
        persistent.setCreateTime(new Date());
        persistent.setSchedulerId(clusterManager.getCurrentNode().getId());
        persistent.setInstanceDetailJson(JSON.toJSONString(requests));
        persistent.setRuleId(rule.getId());
        persistent.setEnabled(false);
        return persistent;
    }

    public SchedulingDistributedRule(Rule rule, String id, ClusterManager clusterManager, EventPublisher eventPublisher, WorkerNodeSelector selector) {
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
            doStop(clusterManager
                    .getAllAliveNode()
                    .stream()
                    .filter(NodeInfo::isWorker)
                    .collect(Collectors.toList()))
                    .thenAccept(nil -> eventPublisher.publishEvent(RuleStopEvent.of(id, rule.getId())));
        });
        context.setOnStart(() -> {
            //启动规则
            start().thenAccept(nil -> eventPublisher.publishEvent(RuleStartEvent.of(id, rule.getId())));
        });

        context.setQueueGetter(nodeId ->
                rule.getModel()
                        .getNode(nodeId)
                        .map(model -> getDataQueueName(id, model))
                        .map(clusterManager::<RuleData>getQueue)
                        .orElseThrow(() -> new NotFoundException("节点[" + nodeId + "]不存在")));

        this.rule = rule;

    }

    private String getDataQueueName(String id, RuleNodeModel model) {
        return "data:" + id + ":node:" + model.getId();
    }


    private void prepare() {
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
            List<NodeInfo> nodes = nodeSelector.select(schedulingRule, clusterManager.getAllAliveNode());
            if (CollectionUtils.isEmpty(nodes)) {
                log.warn("没有可以执行任务[{}-{}]的worker", getContext().getId(), node.getName());
            }
            allRunningNode.addAll(nodes);
            nodeRunnerInfo.put(node.getId(), nodes);
        }
    }

    private List<NodeInfo> getNodeRunnerWorker(String nodeId) {
        return nodeRunnerInfo.getOrDefault(nodeId, Collections.emptyList());
    }

    public CompletionStage<Void> init() {
        prepare();

        return allOf(requests
                .stream()
                .flatMap(request -> getNodeRunnerWorker(request.getNodeId())
                        .stream()
                        .map(nodeInfo -> sendNotify(NotifyType.init, nodeInfo, request, request)))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new));
    }

    @SneakyThrows
    public CompletionStage<Void> tryResume(String workerId) {
        //重新处理节点
        prepare();

        return allOf(requests.stream()
                .flatMap(request -> getNodeRunnerWorker(request.getNodeId())
                        .stream()
                        .filter(workerNode -> workerId.equals(workerNode.getId()))
                        //init and start
                        .map(nodeInfo -> sendNotify(NotifyType.init, nodeInfo, request, request).thenCompose(success ->
                                sendNotify(NotifyType.start, nodeInfo, request, request.getInstanceId()))))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new));

    }

    private CompletionStage<Void> doStop(Collection<NodeInfo> nodeList) {
        changeState(RuleInstanceState.stopping);

        return allOf(nodeList.stream()
                .map(node -> clusterManager
                        .getHaManager()
                        .sendNotify(node.getId(), "rule:stop", context.getId()))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new))
                .whenComplete((nil, error) -> {
                    if (error != null) {
                        changeState(RuleInstanceState.stopFailed);
                    } else {
                        changeState(RuleInstanceState.stopped);
                    }
                });
    }

    private CompletionStage<Void> doStart(NodeInfo node) {
        return clusterManager
                .getHaManager()
                .sendNotify(node.getId(), "rule:start", context.getId());
    }

    public CompletionStage<Void> stop() {
        context.stop();

        return completedFuture(null);
    }

    @SneakyThrows
    public CompletionStage<Void> start() {
        log.info("start rule {}", rule.getId());
        changeState(RuleInstanceState.starting);

        return init().thenCompose(__ -> allOf(clusterManager
                .getAllAliveNode()
                .stream()
                .filter(NodeInfo::isWorker)
                .map(this::doStart)
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new))
                .whenComplete((nil, error) -> {
                    if (error != null) {
                        log.error("启动规则失败", error);
                        changeState(RuleInstanceState.startFailed);
                        stop(); //启动失败,停止任务
                    } else {
                        changeState(RuleInstanceState.started);
                    }
                }));
    }

    private void prepare(RuleNodeModel model) {
        StartRuleNodeRequest request = new StartRuleNodeRequest();
        String id = context.getId();
        request.setSchedulerNodeId(clusterManager.getCurrentNode().getId());
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
        eventPublisher.publishEvent(RuleInstanceStateChangedEvent.of(clusterManager.getCurrentNode().getId(), getContext().getId(), old, state));
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


    private <T> CompletionStage<T> sendNotify(NotifyType type, NodeInfo nodeInfo, StartRuleNodeRequest request, Object notifyData) {
        return clusterManager.getHaManager()
                .<T>sendNotify(nodeInfo.getId(), type.address, notifyData)
                .whenComplete((success, error) -> {
                    if (error != null) {
                        eventPublisher.publishEvent(ClusterRuleErrorEvent.of(type.type, request.getRuleId(), request.getInstanceId(), nodeInfo.getId()));
                        log.warn("{} rule node [{}].[{}] error", type.type, request.getRuleId(), request.getNodeId(), error);
                    } else {
                        eventPublisher.publishEvent(ClusterRuleSuccessEvent.of(type.type, request.getRuleId(), request.getInstanceId(), nodeInfo.getId()));
                    }
                });
    }


}
