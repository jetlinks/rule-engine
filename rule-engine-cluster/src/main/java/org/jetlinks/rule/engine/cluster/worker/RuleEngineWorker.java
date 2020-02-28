package org.jetlinks.rule.engine.cluster.worker;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.cluster.ClusterQueue;
import org.jetlinks.rule.engine.api.ConditionEvaluator;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.events.EventPublisher;
import org.jetlinks.rule.engine.api.events.NodeExecuteEvent;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.Output;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.model.RuleEngineModelParser;
import org.jetlinks.rule.engine.cluster.executor.DefaultContext;
import org.jetlinks.rule.engine.cluster.executor.QueueInput;
import org.jetlinks.rule.engine.cluster.executor.QueueOutput;
import org.jetlinks.rule.engine.cluster.logger.ClusterLogger;
import org.jetlinks.rule.engine.cluster.logger.LogInfo;
import org.jetlinks.rule.engine.cluster.message.EventConfig;
import org.jetlinks.rule.engine.cluster.message.InputConfig;
import org.jetlinks.rule.engine.cluster.message.OutputConfig;
import org.jetlinks.rule.engine.cluster.message.StartRuleNodeRequest;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
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
    private RuleEngineModelParser modelParser;

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
        //初始化
        clusterManager
                .getNotifier()
                .<StartRuleNodeRequest, Boolean>handleNotify("rule:node:init", request -> Mono.just(this.createDistributedRuleNode(request)))
                .subscribe();

        //停止
        clusterManager
                .getNotifier()
                .<String, Boolean>handleNotify("rule:stop", instanceId -> {
                    Optional.ofNullable(allRule.get(instanceId))
                            .map(Map::values)
                            .ifPresent(runningRules -> runningRules.forEach(RunningRule::stop));
                    return Mono.just(true);
                }).subscribe();

        //规则下线
        clusterManager
                .getNotifier()
                .<String, Boolean>handleNotify("rule:down", instanceId -> {
                    Optional.ofNullable(allRule.remove(instanceId))
                            .map(Map::values)
                            .ifPresent(runningRules -> runningRules.forEach(RunningRule::stop));
                    return Mono.just(true);
                }).subscribe();
        // 启动
        clusterManager
                .getNotifier()
                .<String, Boolean>handleNotify("rule:start", instanceId -> {
                    Optional.ofNullable(allRule.get(instanceId))
                            .map(Map::values)
                            .ifPresent(runningRules -> runningRules.forEach(RunningRule::start));
                    return Mono.just(true);
                }).subscribe();
    }

    protected QueueOutput.ConditionQueue createConditionQueue(OutputConfig config) {
        ClusterQueue<RuleData> ruleData = clusterManager.getQueue(config.getQueue());
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

        void doStop();
    }

    private class RunningRuleNode implements RunningRule {
        private ExecutableRuleNode executor;

        private String instanceId;

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
            context.onStop(() -> {
                running = false;
                Optional.ofNullable(allRule.get(instanceId))
                        .map(Map::values)
                        .ifPresent(all -> {
                            log.info("stop rule node {}.{}", ruleId, nodeId);
                            for (RunningRule runningRule : all) {
                                runningRule.doStop();
                            }
                        });
            });
        }

        @Override
        public synchronized void doStop() {
            if (running) {
                stop();
            }
        }

        public void stop() {
            running = false;
            context.stop();
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

    private Mono<Void> returnResult(String instanceId, RuleData data, boolean complete) {
        if (data == null) {
            return Mono.empty();
        }
        String server = data.getAttribute("fromServer").map(String.class::cast).orElse(null);

        if (server != null) {
            data.setAttribute("endServer", clusterManager.getCurrentServerId());
            data.setAttribute("instanceId", instanceId);
            return clusterManager.getNotifier()
                    .sendNotify(server, complete ? "execute-complete" : "execute-result", Mono.just(data))
                    .then();
        }
        return Mono.empty();
    }

    private boolean createDistributedRuleNode(StartRuleNodeRequest request) {
        try {
            Map<String, RunningRule> map = getRunningRuleNode(request.getInstanceId());
            synchronized (map) {
                //已经存在了
                if (map.containsKey(request.getNodeId())) {
                    log.debug("rule node worker {}.{} already exists", request.getRuleId(), request.getNodeId());
                    return true;
                }
                DefaultContext context = new DefaultContext();
                context.setInstanceId(request.getInstanceId());
                context.setNodeId(request.getNodeId());
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
                List<ClusterQueue<RuleData>> inputsQueue = request.getInputQueue()
                        .stream()
                        .map(InputConfig::getQueue)
                        .map(queueName -> clusterManager.<RuleData>getQueue(queueName))
                        .peek(queue -> {
                            if (request.isDistributed()) {
                                //分布式的时候,尝试50%本地消费
                                queue.setLocalConsumerPercent(0.5F);
                            } else {
                                //如果不是分布式,则全部本地消费
                                queue.setLocalConsumerPercent(1F);
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

                context.setEventHandler((event, data) -> Mono.defer(() -> {
                    Mono<Void> mono = Mono.empty();
                    RuleData copy = data.copy();
                    if (RuleEvent.NODE_EXECUTE_DONE.equals(event)) {
                        RuleDataHelper.clearError(copy);
                    }
                    log.debug("fire event {}.{}:{}", configuration.getNodeId(), event, copy);
                    copy.setAttribute("event", event);
                    Output eventOutput = events.get(event);
                    if (eventOutput != null) {
                        mono = eventOutput
                                .write(Mono.just(copy))
                                .doOnError(err -> logger.error("fire event[{}] error", event, err))
                                .then();
                    }
                    if (RuleEvent.NODE_EXECUTE_DONE.equals(event)
                            || RuleEvent.NODE_EXECUTE_RESULT.equals(event)
                            || RuleEvent.NODE_EXECUTE_FAIL.equals(event)) {
                        //同步返回结果
                        if (configuration.getNodeId().equals(RuleDataHelper.getEndWithNodeId(data).orElse(null))) {
                            mono = mono.then(returnResult(request.getInstanceId(), data, !RuleEvent.NODE_EXECUTE_RESULT.equals(event)));
                        }
                    }
                    handleEvent(event, request.getNodeId(), request.getInstanceId(), data);
                    return mono;
                }));
                context.setInput(input);
                context.setOutput(output);
                context.setErrorHandler((ruleData, throwable) -> {
                    RuleDataHelper.putError(ruleData, throwable);
                    context.logger().error(throwable.getMessage(), throwable);
                    return context.fireEvent(RuleEvent.NODE_EXECUTE_FAIL, ruleData);
                });
                context.setLogger(logger);

                RunningRuleNode rule = new RunningRuleNode();
                rule.context = context;
                rule.executor = ruleNode;
                rule.instanceId = request.getInstanceId();
                rule.ruleId = request.getRuleId();
                rule.nodeId = request.getNodeId();

                map.put(request.getNodeId(), rule);
                return true;
            }
        } catch (Exception e) {
            log.error("启动规则[{}]失败", request.getInstanceId(), e);
            throw e;
        }
    }

    private Map<String, RunningRule> getRunningRuleNode(String instanceId) {
        return allRule.computeIfAbsent(instanceId, x -> new ConcurrentHashMap<>());
    }
}
