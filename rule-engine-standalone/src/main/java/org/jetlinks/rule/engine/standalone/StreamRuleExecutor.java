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

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class StreamRuleExecutor implements RuleExecutor {

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

    private Map<String, Object> globalAttr = new HashMap<>();

    private Closeable closeable;

    @Getter
    @Setter
    private List<GlobalNodeEventListener> listeners = new ArrayList<>();

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

    private class SimpleContext implements ExecutionContext {

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
            return StreamRuleExecutor.this::doNext;
        }

        @Override
        public void fireEvent(String event, RuleData data) {
            logger.info("fire event {}.{}:{}", nodeId, event, data);
            data.setAttribute("event", event);
            StreamRuleExecutor.this.fireEvent(event, data);
        }

        @Override
        public void onError(RuleData data, Throwable e) {
            RuleDataHelper.putError(data, e);
            fireEvent(RuleEvent.NODE_EXECUTE_FAIL, data);
        }

        @Override
        public void stop() {

        }

        @Override
        public Logger logger() {
            return logger;
        }

    }

    public void start() {

        next.forEach(RuleExecutor::start);

        SimpleContext context = new SimpleContext();
        closeable = ruleNode.start(context);

    }

    @Override
    @SneakyThrows
    public void stop() {
        if (closeable != null) {
            closeable.close();
        }
        next.forEach(RuleExecutor::stop);
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
        return condition == null || condition.test(data);
    }

    @Override
    public void addEventListener(String event, RuleExecutor executor) {
        eventHandler.computeIfAbsent(event, e -> new ArrayList<>())
                .add(executor);
    }

    @Override
    public void addEventListener(GlobalNodeEventListener listener) {
        listeners.add(listener);
        for (RuleExecutor ruleExecutor : next) {
            ruleExecutor.addEventListener(listener);
        }
    }

}
