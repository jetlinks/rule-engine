package org.jetlinks.rule.engine.cluster.ha;

import org.jetlinks.rule.engine.cluster.NodeInfo;

import java.util.List;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface HaManager {

    /**
     * @return 当前服务节点
     */
    NodeInfo getCurrentNode();

    /**
     * @return 所有可用服务节点
     */
    List<NodeInfo> getAllAliveNode();

    /**
     * 监听服务节点上线
     *
     * @param consumer 监听器
     * @return 当前HaManager对象
     */
    HaManager onNodeJoin(Consumer<NodeInfo> consumer);

    /**
     * 监听服务节点下线
     *
     * @param consumer 监听器
     * @return 当前HaManager对象
     */
    HaManager onNodeLeave(Consumer<NodeInfo> consumer);

    /**
     * 监听来自其他服务的消息通知
     *
     * @param address  消息地址
     * @param consumer 消息消费者
     * @param <T>      消息类型
     */
    <T> void onNotify(String address, Consumer<T> consumer);

    /**
     * 向其他服务节点发送通知
     *
     * @param nodeId  节点ID
     * @param address 通知地址
     * @param message 消息
     */
    void sendNotify(String nodeId, String address, Object message);

}
