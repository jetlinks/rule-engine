package org.jetlinks.rule.engine.api.scope;

/**
 * 规则流作用域,可在同一个规则流中使用
 *
 * @author zhouhao
 * @since 1.1.1
 */
public interface FlowScope extends PersistenceScope {

    NodeScope node(String id);

    ContextScope context(String id);
}
