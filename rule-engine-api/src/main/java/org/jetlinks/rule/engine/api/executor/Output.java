package org.jetlinks.rule.engine.api.executor;

import org.jetlinks.rule.engine.api.RuleData;

/**
 * 数据输出接口,用于在数据吹了完成之后输出结果
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface Output {

    /**
     * 输出规则数据
     *
     * @param data 规则数据
     */
    void write(RuleData data);
}
