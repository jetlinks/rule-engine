package org.jetlinks.rule.engine.executor;

import lombok.SneakyThrows;
import org.hswebframework.utils.ClassUtils;
import org.hswebframework.web.bean.FastBeanCopier;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.executor.RuleNodeConfiguration;
import org.jetlinks.rule.engine.executor.supports.RuleNodeConfig;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public abstract class AbstractExecutableRuleNodeFactoryStrategy<C extends RuleNodeConfig>
        implements GenericConfigExecutableRuleNodeFactoryStrategy<C> {

    private volatile Class<C> configType;

    @SneakyThrows
    public C newConfigInstance() {
        return getConfigType().newInstance();
    }

    @Override
    @SuppressWarnings("all")
    public Class<C> getConfigType() {
        if (configType == null) {
            configType = (Class<C>) ClassUtils.getGenericType(this.getClass(), 0);
        }
        return configType;
    }

    protected abstract ExecutableRuleNode doCreate(C config);

    @Override
    public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
        return doCreate(newConfigInstance(configuration));
    }

    @Override
    public C newConfigInstance(RuleNodeConfiguration configuration) {
        return convertConfig(configuration);
    }

    protected C convertConfig(RuleNodeConfiguration configuration) {
        C config = FastBeanCopier.copy(configuration.getConfiguration(), this::newConfigInstance);
        if (null != configuration.getNodeType()) {
            config.setNodeType(configuration.getNodeType());
        }
        return config;
    }

}
