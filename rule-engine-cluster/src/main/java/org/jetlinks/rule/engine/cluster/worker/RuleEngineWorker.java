package org.jetlinks.rule.engine.cluster.worker;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.NotFoundException;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.cluster.ClusterManager;
import org.jetlinks.rule.engine.api.cluster.Queue;
import org.jetlinks.rule.engine.api.events.EventPublisher;
import org.jetlinks.rule.engine.api.events.EventSupportRuleInstanceContext;
import org.jetlinks.rule.engine.api.events.NodeExecuteEvent;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.Output;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.api.persistent.RulePersistent;
import org.jetlinks.rule.engine.api.persistent.repository.RuleRepository;
import org.jetlinks.rule.engine.cluster.executor.DefaultContext;
import org.jetlinks.rule.engine.cluster.executor.QueueInput;
import org.jetlinks.rule.engine.cluster.executor.QueueOutput;
import org.jetlinks.rule.engine.cluster.logger.ClusterLogger;
import org.jetlinks.rule.engine.cluster.logger.LogInfo;
import org.jetlinks.rule.engine.cluster.message.*;
import org.jetlinks.rule.engine.standalone.StandaloneRuleEngine;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
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

    @Getter
    @Setter
    private EventPublisher eventPublisher;

    @Getter
    @Setter
    private Consumer<NodeExecuteEvent> executeEventConsumer;

    @Getter
    @Setter
    private Consumer<NodeExecuteLogEvent> executeLogEventConsumer;

    private Map<String, Map<String, RunningRule>> allRule = new ConcurrentHashMap<>();

    private volatile boolean running = false;

    public void start() {
        if (running) {
            return;
        }
        running = true;
        if (standaloneRuleEngine == null) {
            StandaloneRuleEngine ruleEngine = new StandaloneRuleEngine();
            ruleEngine.setEvaluator(conditionEvaluator);
            ruleEngine.setNodeFactory(nodeFactory);
            ruleEngine.setLoggerSupplier((contextId, ruleNodeModel) -> {
                ClusterLogger logger = new ClusterLogger();
                logger.setLogInfoConsumer(this::acceptLog);
                logger.setInstanceId(contextId);
                logger.setNodeId(ruleNodeModel.getId());
                logger.setParent(new Slf4jLogger("rule.engine.cluster." + ruleNodeModel.getId()));
                return logger;
            });
            this.standaloneRuleEngine = ruleEngine;
        }
        //初始化
        clusterManager
                .getHaManager()
                .onNotify("rule:node:init", this::createDistributedRuleNode);

        clusterManager
                .getHaManager()
                .onNotify("rule:cluster:init", this::createClusterRule);

        //停止
        clusterManager
                .getHaManager()
                .<String, Boolean>onNotify("rule:stop", instanceId -> {
                    Optional.ofNullable(allRule.get(instanceId))
                            .map(Map::values)
                            .ifPresent(runningRules -> runningRules.forEach(RunningRule::stop));
                    return true;
                });

        //规则下线
        clusterManager
                .getHaManager()
                .<String, Boolean>onNotify("rule:down", instanceId -> {
                    Optional.ofNullable(allRule.remove(instanceId))
                            .map(Map::values)
                            .ifPresent(runningRules -> runningRules.forEach(RunningRule::stop));
                    return true;
                });
        // 启动
        clusterManager
                .getHaManager()
                .<String, Boolean>onNotify("rule:start", instanceId -> {
                    Optional.ofNullable(allRule.get(instanceId))
                            .map(Map::values)
                            .ifPresent(runningRules -> runningRules.forEach(RunningRule::start));
                    return true;
                });
    }

    protected QueueOutput.ConditionQueue createConditionQueue(OutputConfig config) {
        Queue<RuleData> ruleData = clusterManager.getQueue(config.getQueue());
        Predicate<RuleData> ruleDataPredicate = data -> {
            try {
                return conditionEvaluator.evaluate(config.getCondition(), data);
            } catch (Exception e) {
                log.error(e.getMessage(), e);
                return false;
            }
        };
        return new QueueOutput.ConditionQueue(ruleData, ruleDataPredicate);
    }

    private interface RunningRule {
        void start();

        void stop();
    }

    private class RunningRuleNode implements RunningRule {
        private ExecutableRuleNode executor;

        private DefaultContext context;

        private String ruleId;

        private String nodeId;

        volatile boolean running = false;

        public synchronized void start() {
            if (running) {
                return;
            }
            log.info("start rule node {}.{}", ruleId, nodeId);
            executor.start(context);
            running = true;
        }

        public void stop() {
            log.info("stop rule node {}.{}", ruleId, nodeId);
            running = false;
            context.stop();
        }
    }


    private class StandaloneRunningRule implements RunningRule {
        private QueueInput input;
        private RuleInstanceContext context;
        private Logger logger;
        private StartRuleRequest request;

        volatile boolean running = false;

        public StandaloneRunningRule(QueueInput input, RuleInstanceContext context, Logger logger, StartRuleRequest request) {
            this.input = input;
            this.context = context;
            this.logger = logger;
            this.request = request;
        }

        @Override
        public synchronized void start() {
            if (running) {
                return;
            }
            running = true;
            AtomicReference<Function<RuleData, CompletionStage<RuleData>>> reference = new AtomicReference<>();
            context.execute(reference::set);
            input.accept(data -> {
                if (RuleDataHelper.isSync(data)) {
                    context.execute(data)
                            .whenComplete((ruleData, throwable) -> syncReturn(request.getInstanceId(), ruleData, throwable));
                } else {
                    reference.get().apply(data);
                }
            });
            context.start();
            log.debug("start rule {}", request.getRuleId());
        }

        @Override
        public void stop() {
            log.debug("stop rule {}", request.getRuleId());
            input.close();
            context.stop();
            running = false;
        }
    }


    protected void acceptLog(LogInfo logInfo) {
        if (null != executeLogEventConsumer) {
            executeLogEventConsumer.accept(NodeExecuteLogEvent.of(logInfo));
        }
        if (null != eventPublisher) {
            eventPublisher.publishEvent(NodeExecuteLogEvent.of(logInfo));
        }
    }

    protected void handleEvent(String event, String nodeId, String instanceId, RuleData ruleData) {
        if (null != executeEventConsumer) {
            executeEventConsumer.accept(NodeExecuteEvent.of(event, instanceId, nodeId, ruleData));
        }
        if (null != eventPublisher) {
            eventPublisher.publishEvent(NodeExecuteEvent.of(event, instanceId, nodeId, ruleData));
        }
    }

    private void syncReturn(String instanceId, RuleData data, Throwable error) {
        if (data == null) {
            log.error("sync return [{}] error", instanceId, error);
            return;
        }
        String server = data.getAttribute("fromServer").map(String.class::cast).orElse(null);

        if (server != null) {
            data.setAttribute("endServer", clusterManager.getHaManager().getCurrentNode().getId());
            data.setAttribute("instanceId", instanceId);
            clusterManager.getHaManager()
                    .sendNotifyNoReply(server, "sync-return", data);
        }
        if (log.isInfoEnabled()) {
            log.info("sync return:{}", data);
        }
    }


    @Deprecated
    protected boolean createClusterRule(StartRuleRequest request) {
        Map<String, RunningRule> map = getRunningRuleNode(request.getInstanceId());
        synchronized (map) {
            if (map.containsKey(request.getRuleId())) {
                log.debug("rule node worker {} already exists", request.getRuleId());
                return true;
            }
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

            RuleInstanceContext context = standaloneRuleEngine.startRule(rule);
            if (context instanceof EventSupportRuleInstanceContext) {
                EventSupportRuleInstanceContext instanceContext = ((EventSupportRuleInstanceContext) context);
                //添加监听器
                instanceContext.addEventListener(executeEvent -> handleEvent(executeEvent.getEvent(), executeEvent.getNodeId(), executeEvent.getInstanceId(), executeEvent.getRuleData()));
            } else {
                logger.warn("context [{}] not support event!", context);
            }

            StandaloneRunningRule runningRule = new StandaloneRunningRule(input, context, logger, request);

            map.put(request.getRuleId(), runningRule);
        }
        return true;
    }

    //分布式规则
    protected boolean createDistributedRuleNode(StartRuleNodeRequest request) {
        try {
            Map<String, RunningRule> map = getRunningRuleNode(request.getInstanceId());
            synchronized (map) {
                //已经存在了
                if (map.containsKey(request.getNodeId())) {
                    log.debug("rule node worker {}.{} already exists", request.getRuleId(), request.getNodeId());
                    return true;
                }
                DefaultContext context = new DefaultContext();

                RuleNodeConfiguration configuration = request.getNodeConfig();
                log.info("create executor rule worker :{}.{}", request.getInstanceId(), configuration.getNodeId());
                ExecutableRuleNode ruleNode = nodeFactory.create(configuration);
                //事件队列
                Map<String, QueueOutput> events = request.getEventQueue()
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
                        .distinct()
                        .collect(Collectors.toList());

                //输入队列
                List<Queue<RuleData>> inputsQueue = request.getInputQueue()
                        .stream()
                        .map(InputConfig::getQueue)
                        .map(queueName -> clusterManager.<RuleData>getQueue(queueName))
                        .peek(queue -> {
                            if (request.isDistributed()) {
                                //分布式的时候,如果尝试50%本地消费
                                queue.setLocalConsumerPoint(0.5F);
                            } else {
                                //如果不是分布式,则全部本地消费
                                queue.setLocalConsumerPoint(1F);
                            }
                        })
                        .distinct()
                        .collect(Collectors.toList());

                QueueInput input = new QueueInput(inputsQueue);
                QueueOutput output = new QueueOutput(outputQueue);
                ClusterLogger logger = new ClusterLogger();
                logger.setParent(new Slf4jLogger("rule.engine.cluster." + request.getNodeConfig().getId()));
                logger.setContext(request.getLogContext());
                logger.setInstanceId(request.getInstanceId());
                logger.setNodeId(request.getNodeId());
                logger.setLogInfoConsumer(this::acceptLog);

                context.setEventHandler((event, data) -> {
                    data = data.copy();
                    if (RuleEvent.NODE_EXECUTE_DONE.equals(event)) {
                        RuleDataHelper.clearError(data);
                    }
                    log.debug("fire event {}.{}:{}", configuration.getNodeId(), event, data);
                    data.setAttribute("event", event);
                    if (RuleEvent.NODE_EXECUTE_DONE.equals(event) || RuleEvent.NODE_EXECUTE_FAIL.equals(event)) {
                        //同步返回结果
                        if (configuration.getNodeId().equals(RuleDataHelper.getEndWithNodeId(data).orElse(null))) {
                            syncReturn(request.getInstanceId(), data, null);
                        }
                    }
                    Output eventOutput = events.get(event);
                    if (eventOutput != null) {
                        eventOutput.write(data);
                    }
                    handleEvent(event, request.getNodeId(), request.getInstanceId(), data);
                });
                context.setInput(input);
                context.setOutput(output);
                context.setErrorHandler((ruleData, throwable) -> {
                    RuleDataHelper.putError(ruleData, throwable);
                    context.fireEvent(RuleEvent.NODE_EXECUTE_FAIL, ruleData);
                });
                context.setLogger(logger);

                RunningRuleNode rule = new RunningRuleNode();
                rule.context = context;
                rule.executor = ruleNode;
                rule.ruleId = request.getRuleId();
                rule.nodeId = request.getNodeId();

                map.put(request.getNodeId(), rule);
                return true;
            }
        }catch (Exception e){
            log.error("启动规则[{}]失败",request.getInstanceId(), e);
            throw  e;
        }
    }

    private Map<String, RunningRule> getRunningRuleNode(String instanceId) {
        return allRule.computeIfAbsent(instanceId, x -> new ConcurrentHashMap<>());
    }
}
