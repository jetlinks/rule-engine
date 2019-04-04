package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.api.DefaultRuleData;
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
@Slf4j
public class ClusterRuleInstanceContext implements RuleInstanceContext {

    private String id;

    private long startTime;

    private Queue<RuleData> inputQueue;

    private String syncReturnNodeId;

    private ClusterManager clusterManager;

    private Runnable onStop;

    private long syncTimeout = 30_000;

    public RuleData wrapClusterRuleData(RuleData ruleData) {
        return ruleData;
    }

    @Override
    public CompletionStage<RuleData> execute(RuleData data) {

        //标记本条数据需要同步返回结果
        data = RuleDataHelper.markSyncReturn(wrapClusterRuleData(data), syncReturnNodeId);

        String dataId = data.getId();
        log.info("execute rule:{} data:{}", id, data);
        //执行完成的信号，规则执行完成后会由对应的节点去触发。
        ClusterSemaphore semaphore = clusterManager.getSemaphore(dataId, 0);


        //发送数据到规则入口队列
        return inputQueue
                .putAsync(data)
                .thenCompose(nil -> semaphore.tryAcquireAsync(syncTimeout, TimeUnit.MILLISECONDS))
                .thenComposeAsync(isSuccess -> {
                    if (isSuccess) {
                        //如果成功，删除此信号量，因为是一次性的。
                        semaphore.delete();
                    }
                    return clusterManager
                            .<RuleData>getObject(dataId)
                            .getAndDeleteAsync();
                });
    }

    @Override
    public void execute(Consumer<Function<RuleData, CompletionStage<RuleData>>> dataSource) {
        dataSource.accept(data -> {
            //标记了是同步返回
            if (RuleDataHelper.isSync(data)) {
                return execute(data);
            } else {
                //没有标记则直接发送到队列然后返回结果null
                return inputQueue
                        .putAsync(wrapClusterRuleData(data))
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
