package org.jetlinks.rule.engine.standalone;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.events.NodeExecuteEvent;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.executor.Input;
import org.jetlinks.rule.engine.api.executor.Output;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxProcessor;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Slf4j
public class DefaultRuleExecutor implements RuleExecutor {

    @Getter
    @Setter
    private Logger logger;

    @Getter
    @Setter
    private ExecutableRuleNode ruleNode;

    @Getter
    @Setter
    private NodeType nodeType;

    @Getter
    @Setter
    private String instanceId;

    @Getter
    @Setter
    private String nodeId;

    @Setter
    private FluxProcessor<RuleData, RuleData> processor = EmitterProcessor.create(false);

    private volatile ExecutionContext context = new SimpleContext();

    private List<Runnable> stopListener = new ArrayList<>();

    private volatile boolean running;

    @Getter
    @Setter
    private List<GlobalNodeEventListener> listeners = new CopyOnWriteArrayList<>();

    private Set<OutRuleExecutor> outputs = new HashSet<>();

    private Map<String, List<RuleExecutor>> eventHandler = new HashMap<>();

    private Mono<Boolean> doNext(Publisher<RuleData> dataStream) {
        return Flux.from(dataStream)
                .flatMap((data) -> Flux.fromIterable(outputs)
                        .filter(e -> e.getCondition().test(data))
                        .flatMap(outRuleExecutor -> outRuleExecutor.getExecutor().execute(Mono.just(data))))
                .switchIfEmpty(Mono.just(false))
                .then(Mono.just(false));

    }

    public void start() {

        synchronized (this) {
            if (running) {
                return;
            }
            running = true;
            ruleNode.start(context);
        }
        context.onStop(() -> running = false);
    }

    @Override
    @SneakyThrows
    public void stop() {
        synchronized (this) {
            if (context != null) {
                context.stop();
            }
        }
    }

    protected Mono<Void> fireEvent(String event, RuleData ruleData) {
        for (GlobalNodeEventListener listener : listeners) {
            listener.onEvent(NodeExecuteEvent.builder()
                    .event(event)
                    .ruleData(ruleData)
                    .instanceId(instanceId)
                    .nodeId(nodeId)
                    .build());
        }
        return Mono.justOrEmpty(eventHandler.get(event))
                .flatMapIterable(Function.identity())
                .concatMap(ruleExecutor -> ruleExecutor
                        .execute(Mono.just(ruleData))
                        .onErrorContinue((error, obj) -> {
                            log.error("handle event [{}] error : {}", event, ruleData, error);
                        }))
                .then();

    }

    @Override
    public Mono<Boolean> execute(Publisher<RuleData> publisher) {
        if (!processor.hasDownstreams()) {
            return Mono.just(false);
        }
        return Flux.from(publisher)
                .doOnNext(processor::onNext)
                .then(Mono.just(true));
    }

    @Override
    public void addNext(Predicate<RuleData> condition, RuleExecutor executor) {
        outputs.add(new OutRuleExecutor(condition, executor));
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

        @Override
        public Input getInput() {
            return new Input() {
                @Override
                public Flux<RuleData> subscribe() {
                    return processor;
                }

                @Override
                public void close() {
                    processor.onComplete();
                }
            };
        }

        @Override
        public Output getOutput() {
            return DefaultRuleExecutor.this::doNext;
        }

        @Override
        public Mono<Void> fireEvent(String event, RuleData data) {
            return Mono.defer(() -> {
                RuleData copy = data.copy();
                log.debug("fire event {}.{}:{}", nodeId, event, copy);
                copy.setAttribute("event", event);
                return DefaultRuleExecutor.this.fireEvent(event, copy);
            });
        }

        @Override
        public Mono<Void> onError(RuleData data, Throwable e) {
            return Mono.defer(() -> {
                logger().error(e.getMessage(), e);
                RuleDataHelper.putError(data, e);
                return fireEvent(RuleEvent.NODE_EXECUTE_FAIL, data);
            });
        }

        @Override
        public void stop() {
            stopListener.forEach(Runnable::run);
            //  stopListener.clear();
        }

        @Override
        public void onStop(Runnable runnable) {
            stopListener.add(runnable);
        }

        @Override
        public String getInstanceId() {
            return instanceId;
        }

        @Override
        public String getNodeId() {
            return nodeId;
        }

        @Override
        public Logger logger() {
            return logger;
        }

    }

}
