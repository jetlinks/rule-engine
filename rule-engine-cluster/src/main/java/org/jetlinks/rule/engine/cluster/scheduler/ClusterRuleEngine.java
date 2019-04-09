package org.jetlinks.rule.engine.cluster.scheduler;

import com.alibaba.fastjson.JSON;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.hswebframework.web.NotFoundException;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleEngine;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.cluster.RunMode;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.api.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleInstanceRepository;
import org.jetlinks.rule.engine.api.persistent.repository.RuleRepository;
import org.jetlinks.rule.engine.cluster.ClusterManager;
import org.jetlinks.rule.engine.cluster.NodeInfo;
import org.jetlinks.rule.engine.cluster.message.*;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ClusterRuleEngine implements RuleEngine {

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

    protected Map<String, RunningRule> contextCache = new ConcurrentHashMap<>();

    protected Map<String, List<GlobalNodeEventListener>> allEventListener = new ConcurrentHashMap<>();


    protected String getDataQueueName(String id, RuleNodeModel model) {
        return "data:" + id + ":node:" + model.getId();
    }

    interface RunningRule {
        ClusterRuleInstanceContext getContext();

        void start();

        void stop();

        void init();

        void tryResume(String nodeId);

        RuleInstancePersistent toPersistent();
    }

    //分布式流式规则
    private class RunningDistributedRule implements RunningRule {
        @Getter
        private final ClusterRuleInstanceContext context;

        private List<StartRuleNodeRequest> requests;

        private List<NodeInfo> allRunningNode = new ArrayList<>();

        private Rule rule;

        private Map<String, List<NodeInfo>> nodeRunnerInfo = new HashMap<>();

        public RuleInstancePersistent toPersistent() {
            RuleInstancePersistent persistent = new RuleInstancePersistent();
            persistent.setId(context.getId());
            persistent.setCreateTime(new Date());
            persistent.setSchedulerNodeId(clusterManager.getCurrentNode().getId());
            persistent.setInstanceDetailJson(JSON.toJSONString(requests));
            persistent.setRuleId(rule.getId());
            persistent.setRunning(false);
            return persistent;
        }

        public RunningDistributedRule(Rule rule, String id) {
            context = new ClusterRuleInstanceContext();
            requests = new ArrayList<>();
            context.setClusterManager(clusterManager);
            context.setId(id);
            context.setOnStop(() -> {
                doStop(allRunningNode);
                instanceRepository.stopInstance(id);
            });
            context.setOnStart(() -> {
                start();
                instanceRepository.startInstance(id);
            });
            context.setOnListener(eventListener ->
                    allEventListener
                            .computeIfAbsent(id, i -> new ArrayList<>())
                            .add(eventListener));
            this.rule = rule;
        }

        public void init() {
            requests.clear();
            allRunningNode.clear();
            for (RuleNodeModel node : rule.getModel().getNodes()) {
                if (node.isEnd()) {
                    context.setSyncReturnNodeId(node.getId());
                }
                prepare(node);
                //选择执行节点
                List<NodeInfo> nodeInfo = nodeSelector.select(node.getSchedulingRule(), clusterManager.getAllAliveNode());
                if (CollectionUtils.isEmpty(nodeInfo)) {
                    throw new NotFoundException("没有可以执行任务[" + node.getName() + "]的节点");
                }
                allRunningNode.addAll(nodeInfo);
                nodeRunnerInfo.put(node.getId(), nodeInfo);
            }
        }

        @SneakyThrows
        public void tryResume(String nodeId) {
            List<NodeInfo> nodeInfoList = new ArrayList<>();
            for (StartRuleNodeRequest request : requests) {
                for (NodeInfo nodeInfo : nodeRunnerInfo.get(request.getNodeId())) {
                    if (nodeInfo.getId().equals(nodeId)) {
                        log.debug("resume executor node {}.{}", context.getId(), request.getNodeId());
                        clusterManager
                                .getHaManager()
                                .sendNotify(nodeInfo.getId(), "rule:node:init", request)
                                .toCompletableFuture()
                                .get(10, TimeUnit.SECONDS);
                        nodeInfoList.add(nodeInfo);
                    }
                }
            }
            doStart(nodeInfoList);
        }

        private void doStop(List<NodeInfo> nodeList) {
            for (NodeInfo node : nodeList) {
                clusterManager
                        .getHaManager()
                        .sendNotify(node.getId(), "rule:stop", context.getId());
            }
        }

        private void doStart(List<NodeInfo> nodeList) {
            for (NodeInfo node : nodeList) {
                clusterManager
                        .getHaManager()
                        .sendNotify(node.getId(), "rule:start", context.getId());
            }
        }

        public void stop() {
            context.stop();
        }

        @SneakyThrows
        public void start() {
            log.info("start rule {}", rule.getId());
            for (StartRuleNodeRequest request : requests) {
                for (NodeInfo nodeInfo : nodeRunnerInfo.get(request.getNodeId())) {
                    clusterManager
                            .getHaManager()
                            .sendNotify(nodeInfo.getId(), "rule:node:init", request)
                            .toCompletableFuture()
                            .get(20, TimeUnit.SECONDS);
                }
            }
            doStart(allRunningNode);
        }

        private void prepare(RuleNodeModel model) {
            StartRuleNodeRequest request = new StartRuleNodeRequest();
            String id = context.getId();
            request.setSchedulerNodeId(clusterManager.getCurrentNode().getId());
            request.setInstanceId(context.getId());
            request.setNodeId(model.getId());
            request.setRuleId(rule.getId());
            request.setNodeConfig(model.createConfiguration());
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

    }

    //集群模式规则
    private class RunningClusterRule implements RunningRule {
        @Getter
        private final ClusterRuleInstanceContext context;

        private StartRuleRequest request;

        private Rule rule;

        private List<NodeInfo> workerNodeInfo = new ArrayList<>();

        private RunningClusterRule(Rule rule, String id) {
            context = new ClusterRuleInstanceContext();
            context.setId(id);
            context.setClusterManager(clusterManager);
            String inputQueue = "rule:cluster:" + id + ":input";

            context.setInputQueue(clusterManager.getQueue(inputQueue));

            context.setSyncReturnNodeId(rule.getModel()
                    .getNodes().stream()
                    .filter(RuleNodeModel::isEnd)
                    .map(RuleNodeModel::getId)
                    .findFirst()
                    .orElse(null));

            context.setOnStop(() -> {
                stop();
                instanceRepository.stopInstance(id);
            });
            context.setOnStart(() -> {
                start();
                instanceRepository.startInstance(id);
            });
            context.setOnListener(eventListener ->
                    allEventListener
                            .computeIfAbsent(id, i -> new ArrayList<>())
                            .add(eventListener));

            request = new StartRuleRequest();
            request.setInstanceId(id);
            request.setInputQueue(Arrays.asList(inputQueue));
            request.setRuleId(rule.getId());
            this.rule = rule;
        }

        @Override
        public RuleInstancePersistent toPersistent() {
            RuleInstancePersistent persistent = new RuleInstancePersistent();
            persistent.setId(context.getId());
            persistent.setCreateTime(new Date());
            persistent.setSchedulerNodeId(clusterManager.getCurrentNode().getId());
            persistent.setRuleId(rule.getId());
            persistent.setRunning(false);
            return persistent;
        }

        @Override
        public void start() {
            for (NodeInfo nodeInfo : workerNodeInfo) {
                clusterManager.getHaManager()
                        .sendNotify(nodeInfo.getId(), "rule:start", request.getInstanceId());
            }
        }

        @Override
        public void stop() {
            for (NodeInfo nodeInfo : workerNodeInfo) {
                clusterManager.getHaManager()
                        .sendNotify(nodeInfo.getId(), "rule:stop", request.getInstanceId());
            }
        }

        @Override
        @SneakyThrows
        public void init() {
            workerNodeInfo = nodeSelector.select(rule.getModel().getSchedulingRule(), clusterManager.getAllAliveNode());
            for (NodeInfo nodeInfo : workerNodeInfo) {
                clusterManager.getHaManager()
                        .sendNotify(nodeInfo.getId(), "rule:cluster:init", request)
                        .toCompletableFuture()
                        .get(20, TimeUnit.SECONDS);
            }
        }


        @Override
        public void tryResume(String nodeId) {
            workerNodeInfo.stream()
                    .filter(node -> nodeId.equals(node.getId()))
                    .findFirst()
                    .ifPresent(nodeInfo ->
                            clusterManager.getHaManager()
                                    .sendNotify(nodeInfo.getId(), "rule:cluster:init", request)
                                    .whenComplete((success, error) -> {
                                        if (Boolean.TRUE.equals(success)) {
                                            clusterManager.getHaManager()
                                                    .sendNotify(nodeInfo.getId(), "rule:start", request.getInstanceId());
                                        }
                                    }));
        }
    }

    public void start() {
        //监听节点上线
        clusterManager.getHaManager()
                .onNodeJoin(node -> {
                    log.info("resume rule node");
                    for (RunningRule value : contextCache.values()) {
                        value.tryResume(node.getId());
                    }
                });
    }

    @Override
    public RuleInstanceContext startRule(Rule rule) {
        RunningRule runningRule = createRunningRule(rule, IDGenerator.MD5.generate());
        runningRule.init();

        instanceRepository.saveInstance(runningRule.toPersistent());

        RuleInstanceContext context = runningRule.getContext();
        context.start();

        contextCache.put(context.getId(), runningRule);
        return context;
    }

    @Override
    public RuleInstanceContext getInstance(String id) {
        return contextCache.computeIfAbsent(id, instanceId -> {
            RuleInstancePersistent persistent = instanceRepository
                    .findInstanceById(instanceId)
                    .orElseThrow(() -> new NotFoundException("规则实例[" + id + "]不存在"));
            Rule rule = ruleRepository.findRuleById(persistent.getRuleId())
                    .map(rulePersistent -> rulePersistent.toRule(modelParser))
                    .orElseThrow(() -> new NotFoundException("规则[" + persistent.getRuleId() + "]不存在"));

            RunningRule runningRule = createRunningRule(rule, instanceId);
            runningRule.init();
            //runningRule.start();
            return runningRule;
        }).getContext();
    }

    private RunningRule createRunningRule(Rule rule, String instanceId) {
        if (rule.getModel().getRunMode() == RunMode.DISTRIBUTED) {
            return new RunningDistributedRule(rule, instanceId);
        } else {
            return new RunningClusterRule(rule, instanceId);
        }
    }
}
