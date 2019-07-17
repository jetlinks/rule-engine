package org.jetlinks.rule.engine.api;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * 规则实例上下文,一个上下文对应一个运行中的规则
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleInstanceContext {

    /**
     * @return 实例ID
     */
    String getId();

    /**
     *
     * @return 当前状态
     */
    RuleInstanceState getState();

    /**
     * @return 启动时间
     * @see System#currentTimeMillis()
     */
    long getStartTime();

    /**
     * 将数据放入规则中执行，并尝试同步返回结果，当获取超时{@link CompletableFuture#get()}会null
     *
     * @param data 数据
     * @return 执行结果
     * @see RuleData#create(Object)
     * @see CompletionStage
     * @see java.util.concurrent.CompletableFuture
     */
    CompletionStage<RuleData> execute(RuleData data);

    /**
     * 不断的向规则传递数据,如果将
     * <pre>
     *     context.execute(consumer->{
     *
     *        for(int i=0;i<100;i++){
     *           consumer.invoke(RuleData.create("data"+i))
     *           .thenComplete((result,error)->{
     *               //
     *           });
     *        }
     *
     *     });
     * </pre>
     *
     * @param dataSource 数据源
     * @see RuleDataHelper#markSyncReturn(RuleData)
     */
    void execute(Consumer<Function<RuleData, CompletionStage<RuleData>>> dataSource);

    /**
     * 启动规则
     */
    void start();

    /**
     * 停止规则
     */
    void stop();

}
