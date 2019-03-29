package org.jetlinks.rule.engine.executor;

import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.api.executor.StreamExecutionContext;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class AbstractExecutableRuleNodeFactoryStrategy<C>
        implements ExecutableRuleNodeFactoryStrategy {

    public abstract C newConfig();

    public abstract String getSupportType();

    public abstract Function<Object, CompletionStage<Object>> createExecutor(C config);

    public ExecutableRuleNode create(C config) {
        Function<Object, CompletionStage<Object>> executor = createExecutor(config);
        return context -> {
            if (context instanceof StreamExecutionContext) {
                StreamExecutionContext streamContext = ((StreamExecutionContext) context);
                streamContext.getInput()
                        .acceptOnce(data ->
                                executor.apply(data.getData())
                                        .whenComplete((result, error) -> {
                                            if (error != null) {
                                                streamContext.onError(data, error);
                                            } else {
                                                streamContext.getOutput()
                                                        .write(data.newData(result));
                                            }
                                        }));
                return CompletableFuture.completedFuture(null);
            } else {
                return executor.apply(context);
            }
        };
    }

    @Override
    public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
        return create(convertConfig(configuration));
    }

    public C convertConfig(RuleNodeConfiguration configuration) {
        return FastBeanCopier.copy(configuration.getConfiguration(), this::newConfig);
    }
}
