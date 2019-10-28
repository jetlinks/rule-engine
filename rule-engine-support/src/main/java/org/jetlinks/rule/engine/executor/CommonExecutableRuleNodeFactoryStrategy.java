package org.jetlinks.rule.engine.executor;

import com.alibaba.fastjson.JSON;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.supports.RuleNodeConfig;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class CommonExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig>
        extends AbstractExecutableRuleNodeFactoryStrategy<C> {

    public abstract Function<RuleData, Publisher<Object>> createExecutor(ExecutionContext context, C config);

    protected boolean returnNewValue(C config) {
        return config.getNodeType() != null && config.getNodeType().isReturnNewValue();
    }

    protected ExecutableRuleNode doCreate(C config) {
        return context -> {
            Function<RuleData, Publisher<Object>> executor = createExecutor(context, config);
            boolean returnNewValue = returnNewValue(config);

            Disposable disposable = context
                    .getInput()
                    .subscribe()
                    .doOnNext(data -> {
                        RuleDataHelper.setExecuteTimeNow(data);
                        context.fireEvent(RuleEvent.NODE_EXECUTE_BEFORE, data.newData(data));
                    })
                    .doOnSubscribe(sub -> context.fireEvent(RuleEvent.NODE_STARTED, RuleData.create(config)))
                    .subscribe(ruleData -> {

                        Flux.from(executor.apply(ruleData))
                                .map(this::convertObject)
                                .doOnComplete(() -> context.fireEvent(RuleEvent.NODE_EXECUTE_DONE, ruleData.copy()))
                                .map(data -> {
                                    if (returnNewValue) {
                                        return ruleData.newData(data);
                                    }
                                    return ruleData.newData(ruleData);
                                })
                                .switchIfEmpty(Mono.just(ruleData))
                                .cast(RuleData.class)
                                .doOnNext(result -> context.fireEvent(RuleEvent.NODE_EXECUTE_RESULT, result.copy()))
                                .as(context.getOutput()::write)
                                .doOnError(error -> context.onError(ruleData, error))
                                .subscribe();
                    });

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
