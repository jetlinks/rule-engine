package org.jetlinks.rule.engine.standalone;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.events.EventSupportRuleInstanceContext;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.model.Condition;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 单点规则引擎实现
 *
 * @author zhouhao
 * @since 1.0.0
 */
@Slf4j
public class StandaloneRuleEngine implements RuleEngine {

    @Getter
    @Setter
    private ExecutableRuleNodeFactory nodeFactory;

    @Getter
    @Setter
    private ConditionEvaluator evaluator;

    @Getter
    @Setter
    private Executor executor = ForkJoinPool.commonPool();

    @Getter
    @Setter
    private BiFunction<String, RuleNodeModel, Logger> loggerSupplier = (contextId, ruleNodeModel) -> new Slf4jLogger("rule.engine.cluster." + ruleNodeModel.getId());


    public Map<String, RuleInstanceContext> contextMap = new ConcurrentHashMap<>();

    private class RuleExecutorBuilder {
        private Map<String, RuleExecutor> allExecutor = new ConcurrentHashMap<>();

        private RuleExecutor createSingleRuleExecutor(String contextId, Condition condition, RuleNodeModel nodeModel) {
            RuleExecutor tmp = allExecutor.get(nodeModel.getId());

            if (tmp == null) {

                DefaultRuleExecutor executor = new DefaultRuleExecutor();
                allExecutor.put(nodeModel.getId(), tmp = executor);

                ExecutableRuleNode ruleNode = nodeFactory.create(nodeModel.createConfiguration());
                Logger logger = loggerSupplier.apply(contextId, nodeModel);
                executor.setLogger(logger);
                executor.setRuleNode(ruleNode);
                if (null != condition) {
                    executor.setCondition(ruleData -> evaluator.evaluate(condition, ruleData));
                }
                //event
                for (RuleLink event : nodeModel.getEvents()) {
                    executor.addEventListener(event.getType(), createRuleExecutor(contextId, event.getCondition(), event.getTarget(), null));
                }

                executor.setInstanceId(contextId);
                executor.setNodeId(nodeModel.getId());
                executor.setNodeType(nodeModel.getNodeType());
            }
            return tmp;

        }

        private RuleExecutor createRuleExecutor(String contextId, Condition condition, RuleNodeModel nodeModel, RuleExecutor parent) {
            RuleExecutor executor = createSingleRuleExecutor(contextId, condition, nodeModel);
            if (parent != null) {
                parent.addNext(executor);
            }

            //output
            for (RuleLink output : nodeModel.getOutputs()) {
                createRuleExecutor(contextId, output.getCondition(), output.getTarget(), executor);
            }

            return parent != null ? parent : executor;
        }
    }

    @Override
    public RuleInstanceContext startRule(Rule rule) {
        String id = IDGenerator.MD5.generate();
        RuleNodeModel nodeModel = rule.getModel().getStartNode()
                .orElseThrow(() -> new UnsupportedOperationException("无法获取启动节点"));
        RuleExecutorBuilder builder = new RuleExecutorBuilder();

        StandaloneRuleInstanceContext context = new StandaloneRuleInstanceContext();
        context.id = id;
        context.startTime = System.currentTimeMillis();
        context.rootExecutor = builder.createRuleExecutor(id, null, nodeModel, null);
        context.allExecutor = builder.allExecutor;
        rule.getModel()
                .getEndNodes()
                .stream()
                .findFirst()
                .ifPresent(endNode -> context.setEndNodeId(endNode.getId()));
        context.init();
        context.start();
        contextMap.put(id, context);
        return context;
    }

    @Override
    public RuleInstanceContext getInstance(String instanceId) {
        return contextMap.get(instanceId);
    }

    @Getter
    @Setter
    public class StandaloneRuleInstanceContext implements RuleInstanceContext, EventSupportRuleInstanceContext {
        private String id;
        private long startTime;

        private String endNodeId;

        private RuleExecutor rootExecutor;

        private Map<String, RuleExecutor> allExecutor;

        private Map<String, Sync> syncMap = new ConcurrentHashMap<>();

        private RuleExecutor getExecutor(RuleData data) {
            return RuleDataHelper
                    .getStartWithNodeId(data)
                    .map(allExecutor::get)
                    .orElse(rootExecutor);
        }

        @Override
        public CompletionStage<RuleData> execute(RuleData data) {
            if (!RuleDataHelper.isSync(data)) {
                RuleDataHelper.markSyncReturn(data, endNodeId);
            }
            Sync sync = new Sync();
            syncMap.put(data.getId(), sync);
            RuleExecutor ruleExecutor = getExecutor(data);

            return CompletableFuture.supplyAsync(() -> {
                ruleExecutor.execute(data);
                try {
                    sync.countDownLatch.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    syncMap.remove(data.getId());
                }
                if (log.isDebugEnabled()) {
                    log.debug("rule[{}] execute complete:{}", id, sync.ruleData);
                }
                return sync.ruleData;
            }, executor);
        }

        @Override
        public void execute(Consumer<Function<RuleData, CompletionStage<RuleData>>> dataSource) {
            dataSource.accept(data -> getExecutor(data).execute(data));
        }

        @Override
        public void addEventListener(GlobalNodeEventListener listener) {
            for (RuleExecutor ruleExecutor : allExecutor.values()) {
                ruleExecutor.addEventListener(listener);
            }
        }

        @Override
        public void start() {
            for (RuleExecutor ruleExecutor : allExecutor.values()) {
                ruleExecutor.start();
            }
        }

        public void init() {
            GlobalNodeEventListener listener = executeEvent -> {
                String event = executeEvent.getEvent();

                RuleData data = executeEvent.getRuleData();
                data.setAttribute("event", event);
                if (RuleEvent.NODE_EXECUTE_DONE.equals(executeEvent.getEvent())) {
                    RuleDataHelper.clearError(data);
                }
                if ((RuleEvent.NODE_EXECUTE_DONE.equals(event) || RuleEvent.NODE_EXECUTE_FAIL.equals(event)) &&
                        executeEvent.getNodeId().equals(RuleDataHelper.getEndWithNodeId(data).orElse(null))) {
                    Optional.ofNullable(syncMap.remove(data.getId()))
                            .ifPresent(sync -> {
                                sync.ruleData = data;
                                sync.countDownLatch.countDown();
                            });
                }
            };
            for (RuleExecutor ruleExecutor : allExecutor.values()) {
                ruleExecutor.addEventListener(listener);
            }
        }

        @Override
        public void stop() {
            for (RuleExecutor ruleExecutor : allExecutor.values()) {
                ruleExecutor.stop();
            }
        }

    }

    static class Sync {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RuleData ruleData;
    }
}
