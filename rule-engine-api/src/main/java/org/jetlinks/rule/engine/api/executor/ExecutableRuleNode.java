package org.jetlinks.rule.engine.api.executor;


/**
 * 可执行的规则节点
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface ExecutableRuleNode {

    /**
     * 启动规则
     *
     * @param executionContext 执行上下文
     */
    void start(ExecutionContext executionContext);
}
