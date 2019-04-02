package org.jetlinks.rule.engine.cluster.scheduler;

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

import java.util.List;

public class ClusterRuleEngine implements RuleEngine {

    private ClusterManager clusterManager;

    private WorkerNodeSelector nodeSelector;

    protected String getDataQueueName(String id, RuleNodeModel model) {
        return "data:" + id + ":node:" + model.getId();
    }

    @Override
    public RuleInstanceContext startRule(Rule rule) {
        RuleModel model = rule.getModel();
        List<NodeInfo> allAliveNode = clusterManager.getAllAliveNode();
        String id = IDGenerator.MD5.generate();
        ClusterRuleInstanceContext instanceContext = new ClusterRuleInstanceContext();
        instanceContext.setId(id);
        instanceContext.setClusterManager(clusterManager);

        for (RuleNodeModel nodeModel : model.getNodes()) {
            List<NodeInfo> nodeInfo = nodeSelector.select(nodeModel, allAliveNode);
            StartRuleNodeRequest request = new StartRuleNodeRequest();
            request.setInstanceId(id);
            request.setNodeConfig(nodeModel.createConfiguration());

            request.getLogContext().put("ruleId", rule.getId());
            request.getLogContext().put("instanceId", id);
            request.getLogContext().put("nodeId", nodeModel.getId());

            if (nodeModel.isStartNode()) {
                instanceContext.setInputQueue(clusterManager.getQueue(getDataQueueName(id, nodeModel)));
            }
            if (nodeModel.isEndNode()) {
                if (instanceContext.getSyncReturnNodeId() != null) {
                    if (nodeModel.getNodeType().isReturnNewValue()) {
                        instanceContext.setSyncReturnNodeId(nodeModel.getId());
                    }
                } else {
                    instanceContext.setSyncReturnNodeId(nodeModel.getId());
                }
            }

            //event
            for (RuleLink eventLink : nodeModel.getEvents()) {
                RuleNodeModel event = eventLink.getTarget();
                EventConfig inputConfig = new EventConfig();
                inputConfig.setEvent(eventLink.getType());
                inputConfig.setCondition(eventLink.getCondition());
                inputConfig.setQueue(getDataQueueName(id, event));
                request.getEventQueue().add(inputConfig);
            }
            //input
            for (RuleLink inputLink : nodeModel.getInputs()) {
                RuleNodeModel source = inputLink.getSource();
                InputConfig inputConfig = new InputConfig(getDataQueueName(id, source));
                request.getInputQueue().add(inputConfig);
            }
            //output
            for (RuleLink outputLink : nodeModel.getOutputs()) {
                RuleNodeModel target = outputLink.getTarget();
                OutputConfig outputConfig = new OutputConfig();
                outputConfig.setQueue(getDataQueueName(id, target));
                outputConfig.setCondition(outputLink.getCondition());
                request.getOutputQueue().add(outputConfig);
            }
            //推送到执行的服务节点
            for (NodeInfo node : nodeInfo) {
                clusterManager.getQueue("accept:node:" + node.getId())
                        .put(request);
            }
        }

        return instanceContext;
    }

    @Override
    public RuleInstanceContext getInstance(String id) {

        return null;
    }
}
