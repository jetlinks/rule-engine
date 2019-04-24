package org.jetlinks.rule.engine.standalone;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.events.NodeExecuteEvent;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.executor.Output;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultRuleExecutor implements RuleExecutor {

    @Getter
    @Setter
    private Logger logger;

    @Getter
    @Setter
    private ExecutableRuleNode ruleNode;

    @Getter
    @Setter
    private Predicate<RuleData> condition;

    @Getter
    @Setter
    private NodeType nodeType;

    @Getter
    @Setter
    private String instanceId;

    @Getter
    @Setter
    private String nodeId;

    private ExecutionContext context;

    private volatile boolean running;

    @Getter
    @Setter
    private List<GlobalNodeEventListener> listeners = new CopyOnWriteArrayList<>();

    private List<RuleExecutor> next = new ArrayList<>();

    private Map<String, List<RuleExecutor>> eventHandler = new HashMap<>();

    private Consumer<RuleData> consumer = (data) -> {
    };

    private void doNext(RuleData data) {
        for (RuleExecutor ruleExecutor : next) {
            if (ruleExecutor.should(data)) {
                ruleExecutor.execute(data);
            }
        }
    }

    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        context = new SimpleContext();
        ruleNode.start(context);
    }

    @Override
    @SneakyThrows
    public void stop() {
        if (context != null) {
            context.stop();
        }
    }

    protected void fireEvent(String event, RuleData ruleData) {
        for (GlobalNodeEventListener listener : listeners) {
            listener.onEvent(NodeExecuteEvent.builder()
                    .event(event)
                    .ruleData(ruleData)
                    .instanceId(instanceId)
                    .nodeId(nodeId)
                    .build());
        }
        Optional.ofNullable(eventHandler.get(event))
                .filter(CollectionUtils::isNotEmpty)
                .ifPresent(executor -> {
                    for (RuleExecutor ruleExecutor : executor) {
                        ruleExecutor.execute(ruleData);
                    }
                });
    }

    @Override
    @SneakyThrows
    public CompletionStage<RuleData> execute(RuleData ruleData) {

        consumer.accept(ruleData);

        return CompletableFuture.completedFuture(ruleData);
    }

    @Override
    public void addNext(RuleExecutor executor) {
        next.add(executor);
    }

    @Override
    public boolean should(RuleData data) {
        try {
            return condition == null || condition.test(data);
        } catch (Throwable e) {
            logger.error("condition error", e);
            RuleDataHelper.putError(data, e);
            fireEvent(RuleEvent.NODE_EXECUTE_FAIL, data);
            return false;
        }
    }

    @Override
    public void addEventListener(String event, RuleExecutor executor) {
        eventHandler.computeIfAbsent(event, e -> new ArrayList<>())
                .add(executor);
    }

    @Override
    public void addEventListener(GlobalNodeEventListener listener) {
        listeners.add(listener);
    }


    private class SimpleContext implements ExecutionContext {
        private List<Runnable> stopListener = new ArrayList<>();

        @Override
        public Input getInput() {
            return new Input() {
                @Override
                public void accept(Consumer<RuleData> accept) {
                    consumer.andThen(accept);
                }

                @Override
                public boolean acceptOnce(Consumer<RuleData> accept) {
                    consumer = accept;
                    return false;
                }

                @Override
                public void close() {

                }
            };
        }

        @Override
        public Output getOutput() {
            return DefaultRuleExecutor.this::doNext;
        }

        @Override
        public void fireEvent(String event, RuleData data) {
            logger.info("fire event {}.{}:{}", nodeId, event, data);
            data.setAttribute("event", event);
            DefaultRuleExecutor.this.fireEvent(event, data);
        }

        @Override
        public void onError(RuleData data, Throwable e) {
            RuleDataHelper.putError(data, e);
            fireEvent(RuleEvent.NODE_EXECUTE_FAIL, data);
        }

        @Override
        public void stop() {
            stopListener.forEach(Runnable::run);
        }

        @Override
        public void onStop(Runnable runnable) {
            stopListener.add(runnable);
        }

        @Override
        public Logger logger() {
            return logger;
        }

    }


}
