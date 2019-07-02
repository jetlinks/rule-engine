package org.jetlinks.rule.engine.executor;

import com.alibaba.fastjson.JSON;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.executor.supports.RuleNodeConfig;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class AbstractExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig>
        implements ExecutableRuleNodeFactoryStrategy {

    public abstract C newConfig();

    public abstract String getSupportType();

    public abstract Function<RuleData, CompletionStage<Object>> createExecutor(ExecutionContext context, C config);


    protected ExecutableRuleNode doCreate(C config) {
        return context -> {
            Function<RuleData, CompletionStage<Object>> executor = createExecutor(context, config);
            context.getInput()
                    .acceptOnce(data -> {
                        context.fireEvent(RuleEvent.NODE_EXECUTE_BEFORE, data.newData(data));

                        RuleDataHelper.setExecuteTimeNow(data);

                        executor.apply(data)
                                .whenComplete((result, error) -> {
                                    if (error != null) {
                                        context.onError(data, error);
                                    } else {
                                        RuleData newData;
                                        if (config.getNodeType().isReturnNewValue()) {
                                            newData = data.newData(result);
                                        } else {
                                            newData = data.copy();
                                        }
                                        context.fireEvent(RuleEvent.NODE_EXECUTE_DONE, newData);
                                        if (result != SkipNextValue.INSTANCE) {
                                            context.getOutput().write(newData);
                                        }
                                    }
                                });
                    });
        };
    }

    @Override
    public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
        return doCreate(convertConfig(configuration));
    }

    public C convertConfig(RuleNodeConfiguration configuration) {
        C config = FastBeanCopier.copy(configuration.getConfiguration(), this::newConfig);
        config.setNodeType(configuration.getNodeType());
        return config;
    }

    protected Object convertObject(Object object) {
        if (object instanceof String) {
            String stringJson = ((String) object);
            return JSON.parse(stringJson);
        }
        return object;
    }
}
