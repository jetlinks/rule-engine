package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.hswebframework.web.NotFoundException;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.Rule;
import org.jetlinks.rule.engine.api.RuleEngine;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.cluster.ClusterManager;
import org.jetlinks.rule.engine.cluster.NodeInfo;
import org.jetlinks.rule.engine.cluster.message.EventConfig;
import org.jetlinks.rule.engine.cluster.message.InputConfig;
import org.jetlinks.rule.engine.cluster.message.OutputConfig;
import org.jetlinks.rule.engine.cluster.message.StartRuleNodeRequest;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ClusterRuleEngine implements RuleEngine {

    private ClusterManager clusterManager;

    private WorkerNodeSelector nodeSelector;

    protected String getDataQueueName(String id, RuleNodeModel model) {
        return "data:" + id + ":node:" + model.getId();
    }

    private class RunningRule {
        private final ClusterRuleInstanceContext context;

        private List<StartRuleNodeRequest> requests;

        private Rule rule;

        public RunningRule(Rule rule) {
            context = new ClusterRuleInstanceContext();
            requests = new ArrayList<>();

            context.setId(IDGenerator.MD5.generate());
            context.setClusterManager(clusterManager);
            this.rule = rule;
        }

        private void prepare(){
            requests.clear();
            for (RuleNodeModel node : rule.getModel().getNodes()) {
                if(node.isEndNode()){
                    context.setSyncReturnNodeId(node.getId());
                }
                prepare(node);
            }
        }

        private void doStop(List<NodeInfo> nodeInfos) {
            for (NodeInfo node : nodeInfos) {
                clusterManager
                        .getHaManager()
                        .sendNotify(node.getId(), "rule:stop", context.getId());
            }
        }

        private void doStart(List<NodeInfo> nodeInfos) {
            for (NodeInfo node : nodeInfos) {
                clusterManager
                        .getHaManager()
                        .sendNotify(node.getId(), "rule:start", context.getId());
            }
        }

        private void start() {
            prepare();
            List<NodeInfo> allRunningNode = new ArrayList<>();
            try {
                for (StartRuleNodeRequest request : requests) {
                    RuleNodeModel nodeModel = rule.getModel()
                            .getNode(request.getNodeId())
                            .orElseThrow(() -> new NotFoundException("规则节点" + request.getNodeId() + "不存在"));

                    List<NodeInfo> nodeInfo = nodeSelector.select(nodeModel, clusterManager.getAllAliveNode());
                    allRunningNode.addAll(nodeInfo);
                    //推送到执行的服务节点
                    for (NodeInfo node : nodeInfo) {
                        clusterManager
                                .getHaManager()
                                .sendNotify(node.getId(), "accept:node", request);
                    }
                }
            } catch (Throwable e) {
                doStop(allRunningNode);
                throw e;
            }
            doStart(allRunningNode);
        }

        private void prepare(RuleNodeModel model) {
            StartRuleNodeRequest request = new StartRuleNodeRequest();
            String id = context.getId();
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
            if (model.isStartNode()) {
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

    @Override
    public RuleInstanceContext startRule(Rule rule) {
        RunningRule runningRule = new RunningRule(rule);
        runningRule.start();
        return runningRule.context;
    }

    @Override
    public RuleInstanceContext getInstance(String id) {

        return null;
    }
}
