package org.jetlinks.rule.engine.cluster.scheduler;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.cluster.*;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

@Getter
@Setter
public class ClusterRuleInstanceContext implements RuleInstanceContext {

    private String id;

    private long startTime;

    private Queue<RuleData> inputQueue;

    private String syncReturnNodeId;

    private ClusterManager clusterManager;

    private Runnable onStop;

    @Override
    public CompletionStage<RuleData> execute(RuleData data) {
        String dataId = data.getId();

        ClusterSemaphore semaphore = clusterManager.getSemaphore(dataId);

        ClusterMap<String, RuleData> cache = clusterManager.getMap("sync:return:" + id);

        //put attribute
        RuleDataHelper.setSync(data);
        RuleDataHelper.setSyncReturnNodeId(data, syncReturnNodeId);

        //数据入队
        inputQueue.put(data);

        //等待结果
        return semaphore
                .tryAcquireAsync(30, TimeUnit.SECONDS)
                .thenCompose(success -> cache.getAsync(dataId));
    }

    @Override
    public void execute(Consumer<Consumer<RuleData>> dataSink) {
        dataSink.accept(inputQueue::put);
    }

    @Override
    public void stop() {
        if (null != onStop) {
            onStop.run();
        }
    }
}
