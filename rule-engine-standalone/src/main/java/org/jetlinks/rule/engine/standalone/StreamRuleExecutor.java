package org.jetlinks.rule.engine.standalone;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.events.NodeExecuteEvent;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.StreamExecutionContext;
import org.jetlinks.rule.engine.api.executor.StreamRuleNode;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.api.stream.Input;
import org.jetlinks.rule.engine.api.stream.Output;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
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
    private StreamRuleNode ruleNode;

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

    private class SimpleStreamContext implements StreamExecutionContext {

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

        @Override
        public Object getData() {
            return null;
        }

        @Override
        public Map<String, Object> getAttributes() {
            return globalAttr;
        }

        @Override
        public Optional<Object> getAttribute(String key) {
            return Optional.ofNullable(globalAttr.get(key));
        }

        @Override
        public void setAttribute(String key, Object value) {
            globalAttr.put(key, value);
        }
    }

    public void start() {
        SimpleStreamContext context = new SimpleStreamContext();
        ruleNode.start(context);
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
