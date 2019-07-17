package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleInstanceState;
import org.jetlinks.rule.engine.api.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.cluster.NodeInfo;
import org.jetlinks.rule.engine.api.cluster.WorkerNodeSelector;
import org.jetlinks.rule.engine.api.events.EventPublisher;
import org.jetlinks.rule.engine.api.events.RuleInstanceStateChangedEvent;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.cluster.events.ClusterRuleErrorEvent;
import org.jetlinks.rule.engine.cluster.events.ClusterRuleSuccessEvent;
import org.jetlinks.rule.engine.cluster.events.RuleStartEvent;
import org.jetlinks.rule.engine.cluster.events.RuleStopEvent;
import org.jetlinks.rule.engine.cluster.message.StartRuleRequest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.concurrent.CompletableFuture.*;

//集群模式
@Slf4j
class SchedulingClusterRule extends AbstractSchedulingRule {

    private ClusterManager clusterManager;

    private EventPublisher eventPublisher;

    private WorkerNodeSelector nodeSelector;

    @Getter
    private final ClusterRuleInstanceContext context;

    private StartRuleRequest request;

    private Rule rule;

    private List<NodeInfo> workerNodeInfo = new ArrayList<>();

    private AtomicReference<RuleInstanceState> state = new AtomicReference<>(RuleInstanceState.initializing);


    SchedulingClusterRule(Rule rule, String id, ClusterManager clusterManager, EventPublisher eventPublisher, WorkerNodeSelector selector) {
        this.clusterManager = clusterManager;
        this.eventPublisher = eventPublisher;
        this.nodeSelector = selector;

        this.context = new ClusterRuleInstanceContext();
        this.context.setStateSupplier(this::getState);

        String inputQueue = "rule:cluster:" + id + ":input";

        this.request = new StartRuleRequest();
        this.request.setInstanceId(id);
        this.request.setInputQueue(Collections.singletonList(inputQueue));
        this.request.setRuleId(rule.getId());
        this.rule = rule;

        context.setId(id);
        context.setClusterManager(clusterManager);

        context.setInputQueue(clusterManager.getQueue(inputQueue));

        context.setSyncReturnNodeId(rule.getModel()
                .getNodes().stream()
                .filter(RuleNodeModel::isEnd)
                .map(RuleNodeModel::getId)
                .findFirst()
                .orElse(null));

        context.setOnStop(() -> stop()
                .thenAccept((nil) -> eventPublisher.publishEvent(RuleStopEvent.of(id, rule.getId()))));

        context.setOnStart(() -> start()
                .thenAccept((nil) -> eventPublisher.publishEvent(RuleStartEvent.of(id, rule.getId()))));
    }

    @Override
    public RuleInstanceState getState() {
        return state.get();
    }

    @Override
    protected void setState(RuleInstanceState state) {
        this.state.set(state);
    }

    @Override
    public RuleInstancePersistent toPersistent() {
        RuleInstancePersistent persistent = new RuleInstancePersistent();
        persistent.setId(context.getId());
        persistent.setCreateTime(new Date());
        persistent.setSchedulerId(clusterManager.getCurrentNode().getId());
        persistent.setRuleId(rule.getId());
        persistent.setEnabled(false);
        return persistent;
    }

    @Override
    public CompletableFuture<Void> start() {
        changeState(RuleInstanceState.starting);
        return init()
                .thenCompose(__ -> allOf(clusterManager.getAllAliveNode()
                        .stream()
                        .filter(NodeInfo::isWorker)
                        .map(nodeInfo -> this.sendNotify(NotifyType.start, nodeInfo, request.getInstanceId()))
                        .map(CompletionStage::toCompletableFuture)
                        .toArray(CompletableFuture[]::new))
                        .whenComplete((nil, error) -> {
                            if (error != null) {
                                changeState(RuleInstanceState.startFailed);
                            } else {
                                changeState(RuleInstanceState.started);
                            }
                        }));

    }

    @Override
    public CompletableFuture<Void> stop() {
        changeState(RuleInstanceState.stopping);

        return allOf(clusterManager.getAllAliveNode()
                .stream()
                .filter(NodeInfo::isWorker)
                .map(nodeInfo -> this.sendNotify(NotifyType.stop, nodeInfo, request.getInstanceId()))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new))
                .whenComplete((nil, error) -> {
                    if (error != null) {
                        changeState(RuleInstanceState.stopFailed);
                    } else {
                        changeState(RuleInstanceState.started);
                    }
                });
    }

    @Override
    public CompletionStage<Void> tryResume(String nodeId) {
        resetWorker();

        return this.workerNodeInfo.stream()
                .filter(node -> node.getId().equals(nodeId))
                .findFirst()
                .map(node -> this.sendNotify(NotifyType.init, node, request)
                        .thenCompose(e -> this.sendNotify(NotifyType.start, node, request.getInstanceId()))
                        .thenApply(e -> (Void) null))
                .orElseGet(() -> completedFuture(null));
    }

    void changeState(RuleInstanceState state) {
        RuleInstanceState old = getState();
        this.state.set(state);
        eventPublisher.publishEvent(RuleInstanceStateChangedEvent.of(clusterManager.getCurrentNode().getId(), getContext().getId(), old, state));
    }

    private void resetWorker() {
        this.workerNodeInfo = nodeSelector.select(rule.getModel().getSchedulingRule(), clusterManager.getAllAliveNode());
    }


    @Override
    @SneakyThrows
    public CompletableFuture<Void> init() {
        //重置worker信息
        resetWorker();

        //通知所有worker初始化该任务
        return allOf(workerNodeInfo.stream()
                .map(nodeInfo -> this.sendNotify(NotifyType.init, nodeInfo, request))
                .map(CompletionStage::toCompletableFuture)
                .toArray(CompletableFuture[]::new));
    }

    @AllArgsConstructor
    enum NotifyType {
        init("rule:cluster:init", "init"),
        start("rule:start", "start"),
        stop("rule:stop", "stop"),
        ;
        private String address;

        private String type;
    }


    private <T> CompletionStage<T> sendNotify(NotifyType type, NodeInfo nodeInfo, Object data) {
        return clusterManager.getHaManager()
                .<T>sendNotify(nodeInfo.getId(), type.address, data)
                .whenComplete((success, error) -> {
                    if (error != null) {
                        eventPublisher.publishEvent(ClusterRuleErrorEvent.of(type.type, request.getRuleId(), request.getInstanceId(), nodeInfo.getId()));
                        log.warn("{} cluster rule [{}] error", type.type, request.getRuleId(), error);
                    } else {
                        eventPublisher.publishEvent(ClusterRuleSuccessEvent.of(type.type, request.getRuleId(), request.getInstanceId(), nodeInfo.getId()));
                    }
                });
    }

}