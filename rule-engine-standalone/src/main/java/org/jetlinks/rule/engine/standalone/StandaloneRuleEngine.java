package org.jetlinks.rule.engine.standalone;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.*;
import org.jetlinks.rule.engine.api.events.EventSupportRuleInstanceContext;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.api.model.Condition;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.reactivestreams.Publisher;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;
import java.util.function.BiFunction;
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
        private Map<String, DefaultRuleExecutor> allExecutor = new ConcurrentHashMap<>();

        private RuleExecutor createSingleRuleExecutor(String contextId, RuleNodeModel nodeModel) {
            DefaultRuleExecutor tmp = allExecutor.get(nodeModel.getId());

            if (tmp == null) {
                DefaultRuleExecutor executor = new DefaultRuleExecutor();
                allExecutor.put(nodeModel.getId(), tmp = executor);

                ExecutableRuleNode ruleNode = nodeFactory.create(nodeModel.createConfiguration());
                Logger logger = loggerSupplier.apply(contextId, nodeModel);
                executor.setLogger(logger);
                executor.setRuleNode(ruleNode);
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
            RuleExecutor executor = createSingleRuleExecutor(contextId, nodeModel);
            if (parent != null) {
                parent.addNext(condition == null ? (data) -> true : (data) -> evaluator.evaluate(condition, data), executor);
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
        String id = rule.getId();
        RuleInstanceContext old = getInstance(id);
        if (old != null) {
            old.stop();
        }
        RuleNodeModel rootModel = rule.getModel()
                .getStartNode()
                .orElseGet(() -> rule.getModel()
                        .getNodes()
                        .stream()
                        .filter(model -> model.getInputs().isEmpty())
                        .findFirst()
                        .orElseThrow(() -> new UnsupportedOperationException("无法获取启动节点")));

        RuleExecutorBuilder builder = new RuleExecutorBuilder();

        StandaloneRuleInstanceContext context = new StandaloneRuleInstanceContext();
        context.id = id;
        context.startTime = System.currentTimeMillis();

        context.rootExecutor = builder.createRuleExecutor(id, null, rootModel, null);

        //处理所有没有指定输入的节点
        rule.getModel()
                .getNodes()
                .stream()
                .filter(node -> node.getInputs().isEmpty())
                .forEach(node -> builder.createRuleExecutor(id, null, node, null));

        context.allExecutor = new HashMap<>(builder.allExecutor);

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

//        private Map<String, Sync> syncMap = new ConcurrentHashMap<>();

        private Map<String, EmitterProcessor<RuleData>> syncMap = new ConcurrentHashMap<>();


        private RuleExecutor getExecutor(RuleData data) {
            return RuleDataHelper
                    .getStartWithNodeId(data)
                    .map(allExecutor::get)
                    .orElse(rootExecutor);
        }

        @Override
        public Flux<RuleData> execute(Publisher<RuleData> data) {
            Set<String> ids = new HashSet<>();
            return Flux
                    .from(data)
                    .concatMap(ruleData -> {
                        if (!RuleDataHelper.isSync(ruleData)) {
                            RuleDataHelper.markSyncReturn(ruleData, endNodeId);
                        }
                        EmitterProcessor<RuleData> processor = EmitterProcessor.create(true);
                        syncMap.put(ruleData.getId(), processor);
                        Flux<RuleData> flux = processor.map(Function.identity());
                        return getExecutor(ruleData)
                                .execute(Mono.just(ruleData))
                                .flatMapMany(s -> {
                                    if(!s){
                                        return Flux.empty();
                                    }
                                    return flux;
                                });
                    })
                    .timeout(Duration.ofSeconds(30))
                    .doFinally(s -> ids.forEach(syncMap::remove));
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
                if ((RuleEvent.NODE_EXECUTE_RESULT.equals(event)) &&
                        executeEvent.getNodeId().equals(RuleDataHelper.getEndWithNodeId(data).orElse(null))) {
                    Optional.ofNullable(syncMap.get(data.getId()))
                            .ifPresent(sync -> sync.onNext(data));
                }
                if ((RuleEvent.NODE_EXECUTE_DONE.equals(event) || RuleEvent.NODE_EXECUTE_FAIL.equals(event)) &&
                        executeEvent.getNodeId().equals(RuleDataHelper.getEndWithNodeId(data).orElse(null))) {
                    Optional.ofNullable(syncMap.remove(data.getId()))
                            .ifPresent(EmitterProcessor::onComplete);
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
            for (EmitterProcessor<RuleData> value : syncMap.values()) {
                value.error(new InterruptedException("rule stop"));
            }
            syncMap.clear();
        }

        @Override
        public RuleInstanceState getState() {
            return null;
        }


    }

}
