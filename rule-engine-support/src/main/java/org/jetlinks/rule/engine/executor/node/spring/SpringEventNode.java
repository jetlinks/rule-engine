package org.jetlinks.rule.engine.executor.node.spring;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.util.StringUtils;
import reactor.core.publisher.EmitterProcessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

public class SpringEventNode extends CommonExecutableRuleNodeFactoryStrategy<SpringEventConfiguration> {

    @Autowired
    private ApplicationContext eventPublisher;

    private EmitterProcessor<Object> processor = EmitterProcessor.create(false);

    @EventListener
    public void handleEvent(Object event) {
        if (processor.hasDownstreams()) {
            processor.onNext(event);
        }
    }

    @Override
    @SneakyThrows
    public Function<RuleData, ? extends Publisher<?>> createExecutor(ExecutionContext executionContext, SpringEventConfiguration config) {

        if (!StringUtils.hasText(config.getPublishClass())) {
            return Mono::just;
        }
        Class<?> clazz = Class.forName(config.getPublishClass());

        return RuleDataCodecs.getCodec(clazz)
                .<Function<RuleData, ? extends Publisher<?>>>map(codec ->
                        (ruleData) -> codec
                                .decode(ruleData)
                                .doOnNext(eventPublisher::publishEvent)
                                .then())
                .orElseGet(() -> ruleData -> Flux
                        .just(ruleData)
                        .filter(d -> d.getData() != null)
                        .doOnNext((data) -> eventPublisher.publishEvent(data.getData())));
    }

    @Override
    protected void onStarted(ExecutionContext context, SpringEventConfiguration config) {

        if (!StringUtils.hasText(config.getSubscribeClass())) {
            return;
        }
        try {
            Class<?> clazz = Class.forName(config.getSubscribeClass());

            Function<Object, Object> transfer = RuleDataCodecs.getCodec(clazz)
                    .<Function<Object, Object>>map(codec -> (data) -> codec.encode(data))
                    .orElseGet(Function::identity);

            context.onStop(processor.filter(clazz::isInstance)
                    .doOnNext(obj -> context.logger().info("accept spring event: {}", obj))
                    .map(transfer)
                    .map(RuleData::create)
                    .flatMap(data -> context.getOutput().write(Mono.just(data)).thenReturn(data))
                    .doOnNext(ruleData -> context.fireEvent(RuleEvent.NODE_EXECUTE_RESULT, ruleData))
                    .onErrorContinue((err, e) -> context.onError(RuleData.create(e), err).subscribe())
                    .subscribe()::dispose);

        } catch (Exception e) {
            context.onError(RuleData.create(config.getSubscribeClass()), e)
                    .subscribe();
        }

    }

    @Override
    public String getSupportType() {
        return "spring-event";
    }


}
