package org.jetlinks.rule.engine.api.scope;

/**
 * 全局作用域,整个服务中都能用
 *
 * @author zhouhao
 * @since 1.1.1
 */
public interface GlobalScope extends PersistenceScope {

    FlowScope flow(String id);

}
