package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataHelper;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.cluster.*;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

@Getter
@Setter
public class ClusterRuleInstanceContext implements RuleInstanceContext {

    private String id;

    private long startTime;

    private Queue<RuleData> inputQueue;

    private String syncReturnNodeId;

    private ClusterManager clusterManager;

    private Runnable onStop;

    private long syncTimeout = 30_000;

    public RuleData wrapClusterRuleData(RuleData ruleData) {
        if (null == ruleData) {
            return null;
        }
        if (ruleData instanceof ClusterRuleData) {
            ((ClusterRuleData) ruleData).init(clusterManager);
            return ruleData;
        }
        return ClusterRuleData.of(ruleData, clusterManager);
    }

    @Override
    public CompletionStage<RuleData> execute(RuleData data) {
        
        //标记本条数据需要同步返回结果
        data = RuleDataHelper.markSyncReturn(wrapClusterRuleData(data), syncReturnNodeId);

        String dataId = data.getId();

        //执行完成的信号，规则执行完成后会由对应的节点去触发。
        ClusterSemaphore semaphore = clusterManager.getSemaphore(dataId);

        //执行完成后会将结果放入此Map中
        ClusterMap<String, RuleData> returnResult = clusterManager.getMap("sync:return:" + id);

        //发送数据到规则入口队列
        return inputQueue.putAsync(data)
                .thenCompose(nil -> semaphore.tryAcquireAsync(syncTimeout, TimeUnit.MILLISECONDS))
                .thenCompose(isSuccess -> returnResult.getAsync(dataId))
                .thenApply(this::wrapClusterRuleData);

    }

    @Override
    public void execute(Consumer<Function<RuleData, CompletionStage<RuleData>>> dataSource) {
        dataSource.accept(data -> {
            //标记了是同步返回
            if (RuleDataHelper.isSync(data)) {
                return execute(data);
            } else {
                //没有标记则直接发送到队列然后返回结果null
                return inputQueue.putAsync(wrapClusterRuleData(data))
                        .thenApply(nil -> null);
            }
        });
    }


    @Override
    public void stop() {
        if (null != onStop) {
            onStop.run();
        }
    }
}
