package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
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

        public RunningRule() {
            context = new ClusterRuleInstanceContext();
            context.setId(IDGenerator.MD5.generate());
            context.setClusterManager(clusterManager);
        }

        private void start(Rule rule) {
            for (RuleNodeModel node : rule.getModel().getNodes()) {
                if (node.isEnd()) {
                    if (context.getSyncReturnNodeId() == null) {
                        context.setSyncReturnNodeId(node.getId());
                    }
                }
                startNode(rule, node);
            }
        }

        private void startNode(Rule rule, RuleNodeModel model) {
            List<NodeInfo> nodeInfo = nodeSelector.select(model, clusterManager.getAllAliveNode());
            StartRuleNodeRequest request = new StartRuleNodeRequest();
            String id = context.getId();
            request.setInstanceId(context.getId());
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
            //推送到执行的服务节点
            for (NodeInfo node : nodeInfo) {
                clusterManager
                        .getQueue("accept:node:" + node.getId())
                        .put(request);
            }
        }

    }

    @Override
    public RuleInstanceContext startRule(Rule rule) {
        RunningRule runningRule = new RunningRule();
        runningRule.start(rule);
        return runningRule.context;
    }

    @Override
    public RuleInstanceContext getInstance(String id) {

        return null;
    }
}
