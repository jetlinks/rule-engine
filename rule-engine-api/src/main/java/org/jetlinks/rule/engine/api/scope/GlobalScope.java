package org.jetlinks.rule.engine.api.scope;

/**
 * 全局作用域,整个服务中都能用
 *
 * @author zhouhao
 * @since 1.1.1
 */
public interface GlobalScope extends PersistenceScope {

    /**
     * 获取流程作用域
     *
     * @param id ID
     * @return 流程作用域
     */
    FlowScope flow(String id);

}
