package org.jetlinks.rule.engine.executor.supports;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hswebframework.web.bean.Converter;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.TypeConverter;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.jetlinks.rule.engine.executor.AbstractExecutableRuleNodeFactoryStrategy;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Stream;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@SuppressWarnings("all")
@Slf4j
public class JavaMethodInvokeStrategy extends AbstractExecutableRuleNodeFactoryStrategy<JavaMethodInvokeStrategy.JavaMethodInvokeStrategyConfiguration> {

    private TypeConverter typeConverter;

    @Getter
    @Setter
    private ClassLoader classLoader = this.getClass().getClassLoader();

    private static Object[] emptyArgs = new Object[0];

    @Override
    public JavaMethodInvokeStrategyConfiguration newConfig() {
        return new JavaMethodInvokeStrategyConfiguration();
    }

    @Override
    public String getSupportType() {
        return "java-method";
    }

    @SneakyThrows
    public Object getInstance(Class type) {
        return type.newInstance();
    }

    @SneakyThrows
    public Function<RuleData, CompletionStage<Object>> createExecutor(ExecutionContext context, JavaMethodInvokeStrategyConfiguration config) {
        String className = config.getClassName();
        String methodName = config.getMethodName();
        Class clazz = getType(className);
        Method method;
        try {
            method = clazz.getMethod(methodName);
        } catch (Exception e) {
            try {
                method = clazz.getDeclaredMethod(methodName);
            } catch (Exception e2) {
                method = Stream.concat(Stream.of(clazz.getMethods()), Stream.of(clazz.getDeclaredMethods()))
                        .filter(m -> m.getName().equals(methodName))
                        .findFirst()
                        .orElseThrow(() -> new NoSuchMethodException(className + "." + methodName));
            }
        }
        if (!Modifier.isPublic(method.getModifiers())) {
            method.setAccessible(true);
        }
        Object instance = Modifier.isStatic(method.getModifiers()) ? null : getInstance(clazz);
        Method finaleMethod = method;
        int parameterCount = method.getParameterCount();
        Class[] methodTypes = method.getParameterTypes();
        log.debug("create java method invoke executor:{}.{}", className, methodName);
        return (data) -> {
            CompletableFuture future = new CompletableFuture();
            try {
                Object[] invokeParameter = parameterCount > 0 ? new Object[parameterCount] : emptyArgs;
                for (int i = 0; i < parameterCount; i++) {
                    invokeParameter[i] = convertParameter(context, methodTypes[i], data, config, i);
                }
                context.logger().debug("invoke {}.{}", className, methodName);
                Object result = finaleMethod.invoke(instance, (Object[]) invokeParameter);
                if (result instanceof CompletionStage) {
                    return ((CompletionStage) result);
                }
                future.complete(result);
            } catch (Throwable e) {
                future.completeExceptionally(e);
            }
            return future;
        };
    }

    private Converter converter = FastBeanCopier.DEFAULT_CONVERT;

    protected Object convertParameter(ExecutionContext context, Class type, RuleData data,
                                      JavaMethodInvokeStrategyConfiguration config,
                                      int index) {
        String parameter = config.getParameter(index);
        if (Logger.class.equals(type)) {
            return context.logger();
        }
        if (ExecutionContext.class.equals(type)) {
            return context;
        }
        if (RuleData.class.equals(type)) {
            return data;
        }
        if (type.isInstance(data.getData())) {
            return data.getData();
        }

        AtomicReference<Object> reference = new AtomicReference<>();

        data.acceptMap(map -> {
            reference.set(converter.convert(convertParameter(parameter, map),type,null));
        });

        return reference.get();
    }

    protected Object convertParameter(String parameter, Map<String, Object> mapData) {
        return mapData.get(parameter);
    }

    @SneakyThrows
    public Class getType(String className) {
        return classLoader.loadClass(className);
    }

    @Getter
    @Setter
    public static class JavaMethodInvokeStrategyConfiguration implements RuleNodeConfig {
        private String className;

        private String methodName;

        private NodeType nodeType;

        private List<String> parameters;

        public String getParameter(int index) {
            if (parameters == null || parameters.size() <= index) {
                return null;
            }
            return parameters.get(index);
        }
    }

}
