package org.jetlinks.rule.engine.cluster.worker;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.ConditionEvaluator;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.executor.StreamRuleNode;
import org.jetlinks.rule.engine.api.stream.Output;
import org.jetlinks.rule.engine.cluster.ClusterManager;
import org.jetlinks.rule.engine.cluster.Queue;
import org.jetlinks.rule.engine.cluster.logger.ClusterLogger;
import org.jetlinks.rule.engine.cluster.message.EventConfig;
import org.jetlinks.rule.engine.cluster.message.InputConfig;
import org.jetlinks.rule.engine.cluster.message.OutputConfig;
import org.jetlinks.rule.engine.cluster.message.StartRuleNodeRequest;
import org.jetlinks.rule.engine.cluster.stream.DefaultStreamContext;
import org.jetlinks.rule.engine.cluster.stream.QueueInput;
import org.jetlinks.rule.engine.cluster.stream.QueueOutput;

import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Getter
@Setter
@Slf4j
public class RuleEngineWorker {

    private ClusterManager clusterManager;

    private ExecutableRuleNodeFactory nodeFactory;

    private ConditionEvaluator conditionEvaluator;

    public void start() {
        clusterManager
                .<StartRuleNodeRequest>getQueue("accept:node:" + clusterManager.getCurrentNode().getId())
                .acceptOnce(this::startStreamRule);

    }

    protected QueueOutput.ConditionQueue createConditionQueue(OutputConfig config) {
        Queue<RuleData> ruleData = clusterManager.getQueue(config.getQueue());
        Predicate<RuleData> ruleDataPredicate = data -> {
            Object passed = conditionEvaluator.evaluate(config.getCondition(), data);
            return Boolean.TRUE.equals(passed);
        };
        return new QueueOutput.ConditionQueue(ruleData, ruleDataPredicate);
    }

    protected void startStreamRule(StartRuleNodeRequest request) {
        RuleNodeConfiguration configuration = request.getNodeConfig();
        log.info("start stream rule worker :{}.{}", request.getInstanceId(), configuration.getNodeId());
        StreamRuleNode ruleNode = nodeFactory.createStream(configuration);
        //事件输出队列
        Map<String, Output> events = request.getEventQueue()
                .stream()
                .collect(Collectors.groupingBy(EventConfig::getEvent,
                        Collectors.collectingAndThen(Collectors.toList(),
                                list -> new QueueOutput(list.stream()
                                        .map(this::createConditionQueue)
                                        .collect(Collectors.toList())))));

        //输出队列
        List<QueueOutput.ConditionQueue> outputQueue = request.getOutputQueue()
                .stream()
                .map(this::createConditionQueue)
                .collect(Collectors.toList());

        //输入队列
        List<Queue<RuleData>> inputsQueue = request.getInputQueue()
                .stream()
                .map(InputConfig::getQueue)
                .map(queueName -> clusterManager.<RuleData>getQueue(queueName))
                .collect(Collectors.toList());

        QueueInput input = new QueueInput(inputsQueue);
        QueueOutput output = new QueueOutput(outputQueue);
        ClusterLogger logger = new ClusterLogger();
        logger.setParent(new Slf4jLogger("rule.engine.cluster." + request.getNodeConfig().getId()));
        logger.setContext(request.getLogContext());

        logger.setLogInfoConsumer(logInfo -> {
            // TODO: 19-4-4
        });

        DefaultStreamContext context = new DefaultStreamContext() {
            @Override
            public void fireEvent(String event, RuleData data) {
                data = data.newData(data);
                log.info("fire event {}.{}:{}", configuration.getNodeId(), event, data);
                data.setAttribute("event", event);
                if (RuleEvent.NODE_EXECUTE_BEFORE.equals(event)) {
                    data.setAttribute("nodeId", configuration.getId());
                    data.setAttribute("instanceId", request.getInstanceId());
                } else if (RuleEvent.NODE_EXECUTE_DONE.equals(event) || RuleEvent.NODE_EXECUTE_FAIL.equals(event)) {
                    //同步返回结果
                    if (configuration.getNodeId().equals(RuleDataHelper.getSyncReturnNodeId(data))) {
                        logger.info("同步返回结果:{}", data);
                        clusterManager
                                .getObject(data.getId())
                                .setData(data);
                        clusterManager
                                .getSemaphore(data.getId(), 0)
                                .release();
                    }
                }
                Output eventOutput = events.get(event);
                if (eventOutput != null) {
                    eventOutput.write(data);
                }
            }
        };
        context.setInput(input);
        context.setOutput(output);
        context.setErrorHandler((ruleData, throwable) -> {
            RuleDataHelper.putError(ruleData, throwable);
            context.fireEvent(RuleEvent.NODE_EXECUTE_FAIL, ruleData);
        });
        context.setLogger(logger);

        ruleNode.start(context);
    }

}
