package org.jetlinks.rule.engine.executor;

import com.alibaba.fastjson.JSON;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.logger.ReactiveLogger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.node.RuleNodeConfig;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@Slf4j
@Deprecated
public abstract class CommonExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig>
        extends AbstractExecutableRuleNodeFactoryStrategy<C> {

    public abstract Function<RuleData, ? extends Publisher<?>> createExecutor(ExecutionContext context, C config);

    protected boolean returnNewValue(C config) {
        return config.getNodeType() != null && config.getNodeType().isReturnNewValue();
    }

    protected void onStarted(ExecutionContext context, C config) {

    }


    protected ExecutableRuleNode doCreate(C config) {
        config.validate();
        return context -> {
            Function<RuleData, ? extends Publisher<?>> executor = createExecutor(context, config);
            boolean returnNewValue = returnNewValue(config);

            Disposable disposable = context
                    .getInput()
                    .subscribe()
                    .doOnSubscribe(r -> context.fireEvent(RuleEvent.NODE_STARTED, RuleData.create(config)).subscribe())
                    .flatMap(data -> {
                        RuleDataHelper.setExecuteTimeNow(data);
                        return context.fireEvent(RuleEvent.NODE_EXECUTE_BEFORE, data.newData(data))
                                .doOnEach(ReactiveLogger.onError(err -> log.error(err.getMessage(), err)))
                                .onErrorResume(err -> Mono.empty()).thenReturn(data);
                    })
                    .subscribe(ruleData -> {
                        Flux.from(executor.apply(ruleData))
                                .map(this::convertObject)
                                .map(data -> {
                                    if (returnNewValue) {
                                        return ruleData.newData(data);
                                    }
                                    return ruleData.newData(ruleData);
                                })
                                .cast(RuleData.class)
                                .flatMap(result -> context.fireEvent(RuleEvent.NODE_EXECUTE_RESULT, result.copy()).thenReturn(result))
                                .as(context.getOutput()::write)
                                .doOnError(error -> context.onError(ruleData, error).subscribe())
                                .doFinally((result) -> context.fireEvent(RuleEvent.NODE_EXECUTE_DONE, ruleData.copy()).subscribe())
                                .subscribe();
                    });
            onStarted(context, config);
            context.onStop(disposable::dispose);
        };
    }

    protected Object convertObject(Object object) {
        if (object instanceof String) {
            String stringJson = ((String) object);
            if (stringJson.startsWith("[") || stringJson.startsWith("{")) {
                return JSON.parse(stringJson);
            }

        }
        return object;
    }
}
