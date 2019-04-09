package org.jetlinks.rule.engine.cluster.worker;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.NotFoundException;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleRepository;
import org.jetlinks.rule.engine.api.executor.Output;
import org.jetlinks.rule.engine.cluster.ClusterManager;
import org.jetlinks.rule.engine.cluster.Queue;
import org.jetlinks.rule.engine.cluster.logger.ClusterLogger;
import org.jetlinks.rule.engine.cluster.logger.LogInfo;
import org.jetlinks.rule.engine.cluster.message.*;
import org.jetlinks.rule.engine.cluster.executor.DefaultContext;
import org.jetlinks.rule.engine.cluster.executor.QueueInput;
import org.jetlinks.rule.engine.cluster.executor.QueueOutput;
import org.jetlinks.rule.engine.standalone.StandaloneRuleEngine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author zhouhao
 * @since 1.0.0
 */

@Slf4j
public class RuleEngineWorker {

    @Getter
    @Setter
    private ClusterManager clusterManager;

    @Getter
    @Setter
    private ExecutableRuleNodeFactory nodeFactory;

    @Getter
    @Setter
    private ConditionEvaluator conditionEvaluator;

    @Getter
    @Setter
    private RuleRepository ruleRepository;

    @Getter
    @Setter
    private RuleEngineModelParser modelParser;

    @Getter
    @Setter
    private RuleEngine standaloneRuleEngine;

    private Map<String, Map<String, RunningRuleNode>> allRule = new ConcurrentHashMap<>();

    private boolean running = false;

    public void start() {
        if (running) {
            return;
        }
        running = true;
        if (standaloneRuleEngine == null) {
            StandaloneRuleEngine ruleEngine = new StandaloneRuleEngine();
            ruleEngine.setEvaluator(conditionEvaluator);
            ruleEngine.setNodeFactory(nodeFactory);
            this.standaloneRuleEngine = ruleEngine;
        }
        //初始化
        clusterManager
                .getHaManager()
                .onNotify("rule:executor:init", this::createStreamRule);

        clusterManager
                .getHaManager()
                .onNotify("rule:cluster:init", this::createClusterRule);

        //停止
        clusterManager
                .getHaManager()
                .<String, Boolean>onNotify("rule:stop", instanceId -> {
                    for (RunningRuleNode value : getRunningRuleNode(instanceId).values()) {
                        value.stop();
                    }
                    allRule.remove(instanceId);
                    return true;
                });
        // 启动
        clusterManager
                .getHaManager()
                .<String, Boolean>onNotify("rule:start", instanceId -> {
                    for (RunningRuleNode value : getRunningRuleNode(instanceId).values()) {
                        value.start();
                    }
                    return true;
                });
    }

    protected QueueOutput.ConditionQueue createConditionQueue(OutputConfig config) {
        Queue<RuleData> ruleData = clusterManager.getQueue(config.getQueue());
        Predicate<RuleData> ruleDataPredicate = data -> {
            Object passed = conditionEvaluator.evaluate(config.getCondition(), data);
            return Boolean.TRUE.equals(passed);
        };
        return new QueueOutput.ConditionQueue(ruleData, ruleDataPredicate);
    }

    private interface RunningRuleNode {
        void start();

        void stop();
    }

    private class StreamRule implements RunningRuleNode {
        ExecutableRuleNode executor;

        DefaultContext context;
        private String ruleId;

        private String nodeId;

        volatile boolean running = false;

        public synchronized void start() {
            if (running) {
                return;
            }
            log.debug("start rule node {}.{}", ruleId, nodeId);
            running = true;
            executor.start(context);
        }

        public void stop() {
            log.debug("stop rule node {}.{}", ruleId, nodeId);
            context.stop();
        }
    }


    @AllArgsConstructor
    private class ClusterRule implements RunningRuleNode {
        private QueueInput input;
        private RuleInstanceContext context;
        private Logger logger;
        private StartRuleRequest request;

        @Override
        public void start() {
            AtomicReference<Function<RuleData, CompletionStage<RuleData>>> reference = new AtomicReference<>();
            context.execute(reference::set);
            input.acceptOnce(data -> {
                if (RuleDataHelper.isSync(data)) {
                    context.execute(data)
                            .whenComplete((ruleData, throwable) -> {
                                logger.info("sync return:{}", ruleData);
                                clusterManager
                                        .getObject(ruleData.getId())
                                        .setData(ruleData);
                                clusterManager
                                        .getSemaphore(ruleData.getId(), 0)
                                        .release();
                            });
                } else {
                    reference.get()
                            .apply(data);
                }
            });
            log.debug("start rule {}", request.getRuleId());
        }

        @Override
        public void stop() {
            log.debug("stop rule {}", request.getRuleId());
            input.close();
            context.stop();
        }
    }

    protected void acceptLog(LogInfo logInfo) {
        // TODO: 19-4-9
    }

    protected synchronized boolean createClusterRule(StartRuleRequest request) {
        String instanceId = request.getInstanceId();
        String ruleId = request.getRuleId();
        RulePersistent persistent = ruleRepository.findRuleById(ruleId)
                .orElseThrow(() -> new NotFoundException("规则[" + ruleId + "]不存在"));
        Rule rule = persistent.toRule(modelParser);
        //输入队列
        List<Queue<RuleData>> inputsQueue = request.getInputQueue()
                .stream()
                .map(queueName -> clusterManager.<RuleData>getQueue(queueName))
                .collect(Collectors.toList());
        QueueInput input = new QueueInput(inputsQueue);
        ClusterLogger logger = new ClusterLogger();
        logger.setParent(new Slf4jLogger("rule.engine.cluster." + request.getRuleId()));
        logger.setLogInfoConsumer(this::acceptLog);

        getRunningRuleNode(request.getInstanceId())
                .put(request.getRuleId(),
                        new ClusterRule(input, standaloneRuleEngine.startRule(rule), logger, request));

        return true;
    }

    //分布式流式规则
    protected synchronized boolean createStreamRule(StartStreamRuleNodeRequest request) {
        //已经存在了
        if (getRunningRuleNode(request.getInstanceId()).containsKey(request.getNodeId())) {
            log.info("rule node worker {}.{} already exists", request.getRuleId(), request.getNodeId());
            return true;
        }
        RuleNodeConfiguration configuration = request.getNodeConfig();
        log.info("create executor rule worker :{}.{}", request.getInstanceId(), configuration.getNodeId());
        ExecutableRuleNode ruleNode = nodeFactory.create(configuration);
        //事件队列
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

        logger.setLogInfoConsumer(this::acceptLog);

        DefaultContext context = new DefaultContext() {
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
                        logger.info("sync return:{}", data);
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

        StreamRule rule = new StreamRule();
        rule.context = context;
        rule.executor = ruleNode;
        rule.ruleId = request.getRuleId();
        rule.nodeId = request.getNodeId();

        getRunningRuleNode(request.getInstanceId()).put(request.getNodeId(), rule);
        return true;
    }

    private Map<String, RunningRuleNode> getRunningRuleNode(String instanceId) {
        return allRule.computeIfAbsent(instanceId, x -> new HashMap<>());
    }
}
