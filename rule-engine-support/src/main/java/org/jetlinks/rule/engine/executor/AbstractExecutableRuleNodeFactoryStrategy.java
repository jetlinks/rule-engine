package org.jetlinks.rule.engine.executor;

import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.executor.StreamRuleNode;
import org.jetlinks.rule.engine.executor.supports.RuleNodeConfig;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.BiFunction;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class AbstractExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig>
        implements ExecutableRuleNodeFactoryStrategy {

    public abstract C newConfig();

    public abstract String getSupportType();

    public abstract BiFunction<Logger, Object, CompletionStage<Object>> createExecutor(C config);

    public ExecutableRuleNode create(C config) {
        BiFunction<Logger, Object, CompletionStage<Object>> executor = createExecutor(config);
        return context -> {
            Object data = context.getData();
            CompletionStage<Object> stage = executor.apply(context.logger(), data);
            if (config.getNodeType().isReturnNewValue()) {
                return stage;
            } else {
                CompletableFuture<Object> real = new CompletableFuture<>();
                stage.whenComplete((result, error) -> {
                    if (error != null) {
                        real.completeExceptionally(error);
                    } else {
                        real.complete(data);
                    }
                });
                return real;
            }
        };
    }

    public StreamRuleNode createStream(C config) {
        BiFunction<Logger, Object, CompletionStage<Object>> executor = createExecutor(config);
        return context -> context.getInput()
                .acceptOnce(data -> {
                    context.fireEvent(RuleEvent.NODE_EXECUTE_BEFORE, data);
                    executor.apply(context.logger(), data.getData())
                            .whenCompleteAsync((result, error) -> {
                                if (error != null) {
                                    context.onError(data, error);
                                } else {
                                    RuleData newData;
                                    if (config.getNodeType().isReturnNewValue()) {
                                        newData = data.newData(result);
                                    } else {
                                        newData = data;
                                    }
                                    context.fireEvent(RuleEvent.NODE_EXECUTE_DONE, newData);
                                    context.getOutput().write(newData);
                                }
                            });
                });
    }

    @Override
    public StreamRuleNode createStream(RuleNodeConfiguration configuration) {
        return createStream(convertConfig(configuration));
    }

    @Override
    public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
        return create(convertConfig(configuration));
    }

    public C convertConfig(RuleNodeConfiguration configuration) {
        C config = FastBeanCopier.copy(configuration.getConfiguration(), this::newConfig);
        config.setNodeType(configuration.getNodeType());
        return config;
    }
}
