package org.jetlinks.rule.engine.cluster;

import reactor.core.publisher.Flux;

import javax.annotation.Nonnull;

/**
 * 规则实例持久化仓库
 *
 * @author zhouhao
 * @since 1.1.7
 */
public interface RuleInstanceRepository {

    /**
     * 获取全部规则实例
     *
     * @return 规则实例
     */
    @Nonnull
    Flux<RuleInstance> findAll();

    /**
     * 根据ID获取实例
     *
     * @return 规则实例
     */
    @Nonnull
    Flux<RuleInstance> findById(String id);

}
