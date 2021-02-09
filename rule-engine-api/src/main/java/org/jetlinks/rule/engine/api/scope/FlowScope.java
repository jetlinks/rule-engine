package org.jetlinks.rule.engine.api.scope;

/**
 * 规则流作用域,可在同一个规则流中使用
 *
 * @author zhouhao
 * @since 1.1.1
 */
public interface FlowScope extends PersistenceScope {

    /**
     * 获取一个节点的作用域
     *
     * @param id 节点ID
     * @return 节点作用域
     */
    NodeScope node(String id);

    /**
     * 获取一个上下文作用域
     *
     * @param id 上下文ID
     * @return 上下文作用域
     */
    ContextScope context(String id);
}
