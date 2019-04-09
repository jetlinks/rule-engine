package org.jetlinks.rule.engine.standalone;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.id.IDGenerator;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.events.EventSupportRuleInstanceContext;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.model.Condition;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
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

    public Map<String, RuleInstanceContext> contextMap = new ConcurrentHashMap<>();

    public RuleExecutor createSingleRuleExecutor(String contextId, Condition condition, RuleNodeModel nodeModel) {
        ExecutableRuleNode ruleNode = nodeFactory.create(nodeModel.createConfiguration());

        Logger logger = new Slf4jLogger("rule.engine.node." + nodeModel.getId());
        StreamRuleExecutor executor = new StreamRuleExecutor();
        executor.setLogger(logger);
        executor.setRuleNode(ruleNode);
        if (null != condition) {
            executor.setCondition(ruleData -> Boolean.TRUE.equals(evaluator.evaluate(condition, ruleData)));
        }
        //event
        for (RuleLink output : nodeModel.getEvents()) {
            executor.addEventListener(output.getType(), createRuleExecutor(contextId, output.getCondition(), output.getTarget(), null));
        }

        executor.setInstanceId(contextId);
        executor.setNodeId(nodeModel.getId());
        executor.setNodeType(nodeModel.getNodeType());
        return executor;
    }

    public RuleExecutor createRuleExecutor(String contextId, Condition condition, RuleNodeModel nodeModel, RuleExecutor parent) {
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


    @Override
    public RuleInstanceContext startRule(Rule rule) {
        String id = IDGenerator.MD5.generate();
        RuleNodeModel nodeModel = rule.getModel().getStartNode()
                .orElseThrow(() -> new UnsupportedOperationException("无法获取启动节点"));

        StandaloneRuleInstanceContext context = new StandaloneRuleInstanceContext();
        context.id = id;
        context.startTime = System.currentTimeMillis();
        context.rootExecutor = createRuleExecutor(id, null, nodeModel, null);
        rule.getModel()
                .getEndNodes()
                .stream()
                .findFirst()
                .ifPresent(endNode -> context.setEndNodeId(endNode.getId()));
        contextMap.put(id, context);
        context.start();
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

        private Map<String, Sync> syncMap = new ConcurrentHashMap<>();


        @Override
        public CompletionStage<RuleData> execute(RuleData data) {
            if (!RuleDataHelper.isSync(data)) {
                RuleDataHelper.markSyncReturn(data, endNodeId);
            }
            Sync sync = new Sync();
            syncMap.put(data.getId(), sync);

            return CompletableFuture.supplyAsync(() -> {
                rootExecutor.execute(data);
                try {
                    sync.countDownLatch.await(30, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                log.info("rule[{}] execute complete:{}", id, sync.ruleData);
                return sync.ruleData;
            }, executor);
        }

        @Override
        public void execute(Consumer<Function<RuleData, CompletionStage<RuleData>>> dataSource) {
            dataSource.accept(data -> rootExecutor.execute(data));
        }

        @Override
        public void addEventListener(GlobalNodeEventListener listener) {
            rootExecutor.addEventListener(listener);
        }

        @Override
        public void start() {
            rootExecutor.addEventListener(executeEvent -> {
                RuleData data = executeEvent.getRuleData();
                if (!RuleEvent.NODE_EXECUTE_BEFORE.equals(executeEvent.getEvent()) &&
                        executeEvent.getNodeId().equals(RuleDataHelper.getSyncReturnNodeId(data))) {
                    Optional.ofNullable(syncMap.remove(data.getId()))
                            .ifPresent(sync -> {
                                sync.ruleData = data;
                                sync.countDownLatch.countDown();
                            });
                }
            });

            rootExecutor.start();
        }

        @Override
        public void stop() {
            rootExecutor.stop();
        }
    }

    static class Sync {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        RuleData ruleData;
    }
}
