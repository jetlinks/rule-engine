package org.jetlinks.rule.engine.executor;

import com.alibaba.fastjson.JSON;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.supports.RuleNodeConfig;

import java.util.concurrent.CompletionStage;
import java.util.function.Function;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class CommonExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig>
        extends AbstractExecutableRuleNodeFactoryStrategy<C> {

    public abstract Function<RuleData, CompletionStage<Object>> createExecutor(ExecutionContext context, C config);

    protected boolean returnNewValue(C config) {
        return config.getNodeType() != null && config.getNodeType().isReturnNewValue();
    }

    protected ExecutableRuleNode doCreate(C config) {
        return context -> {
            Function<RuleData, CompletionStage<Object>> executor = createExecutor(context, config);
            context.getInput()
                    .accept(data -> {
                        context.fireEvent(RuleEvent.NODE_EXECUTE_BEFORE, data.newData(data));

                        RuleDataHelper.setExecuteTimeNow(data);
                        try {
                            executor.apply(data)
                                    .whenComplete((result, error) -> {
                                        if (error != null) {
                                            context.onError(data, error);
                                        } else {
                                            RuleData newData;
                                            if (returnNewValue(config) && result != SkipNextValue.INSTANCE) {
                                                if (result instanceof RuleData) {
                                                    newData = ((RuleData) result);
                                                } else {
                                                    newData = data.newData(result);
                                                }
                                            } else {
                                                newData = data.copy();
                                            }
                                            context.fireEvent(RuleEvent.NODE_EXECUTE_DONE, newData);
                                            if (result != SkipNextValue.INSTANCE) {
                                                context.getOutput().write(newData);
                                            }
                                        }
                                    });
                        } catch (Throwable e) {
                            context.onError(data, e);
                        }
                    });

            context.fireEvent(RuleEvent.NODE_STARTED, RuleData.create(config));

        };
    }

    protected Object convertObject(Object object) {
        if (object instanceof String) {
            String stringJson = ((String) object);
            return JSON.parse(stringJson);
        }
        return object;
    }
}
