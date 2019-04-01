package org.jetlinks.rule.engine.singleton;

import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.apache.commons.collections.CollectionUtils;
import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.events.RuleEvent;
import org.jetlinks.rule.engine.api.executor.ExecutableRuleNode;
import org.jetlinks.rule.engine.api.model.NodeType;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * @author zhouhao
 * @since 1.0.0
 */
@SuppressWarnings("unchecked")
public class SimpleRuleExecutor implements RuleExecutor {

    @Getter
    @Setter
    private NodeType nodeType = NodeType.MAP;

    private ExecutableRuleNode executableRuleNode;

    private Logger logger;

    private Predicate<RuleData> condition;

    private List<RuleExecutor> next = new ArrayList<>();

    private Map<String, List<RuleExecutor>> eventHandler = new HashMap<>();

    @Override
    public void addEventListener(String event, RuleExecutor executor) {
        eventHandler.computeIfAbsent(event, e -> new ArrayList<>())
                .add(executor);
    }

    public SimpleRuleExecutor(ExecutableRuleNode executableRuleNode, Logger logger) {
        this.executableRuleNode = executableRuleNode;
        this.logger = logger;
    }

    public void setCondition(Predicate<RuleData> condition) {
        this.condition = condition;
    }

    public void addNext(RuleExecutor executor) {
        next.add(executor);
    }

    @Override
    public CompletionStage<RuleData> execute(RuleData ruleData) {
        try {
            fireEvent(RuleEvent.NODE_EXECUTE_BEFORE, ruleData, null);
        } catch (Throwable e) {
            CompletableFuture future = new CompletableFuture();
            future.completeExceptionally(e);
            return future;
        }
        return executableRuleNode
                .execute(new SingletonExecutionContext(logger, ruleData.getData()))
                .handle((result, error) -> {
                    if (error != null) {
                        fireEvent(RuleEvent.NODE_EXECUTE_FAIL, ruleData, error);
                        return null;
                    } else {
                        RuleData data = ruleData.newData(result);
                        fireEvent(RuleEvent.NODE_EXECUTE_DONE, data, null);
                        return data;
                    }
                })
                .thenCompose(result -> {
                    if (null != result) {
                        return next(result);
                    }
                    return CompletableFuture.completedFuture(RuleData.create(null));
                });
    }

    protected void fireEvent(String event, RuleData ruleData, Throwable err) {
        if (err != null) {
            ruleData.setAttribute("error", err);
        }
        Optional.ofNullable(eventHandler.get(event))
                .filter(CollectionUtils::isNotEmpty)
                .ifPresent(executor -> {
                    for (RuleExecutor ruleExecutor : executor) {
                        ruleExecutor.execute(ruleData);
                    }
                });
    }

    @SneakyThrows
    protected CompletionStage<RuleData> next(RuleData data) {
        if (CollectionUtils.isEmpty(next)) {
            return CompletableFuture.completedFuture(data);
        }
        CompletableFuture<RuleData> stage = new CompletableFuture<>();
        List<RuleExecutor> all = next.stream()
                .filter(executor -> executor.should(data))
                .collect(Collectors.toList());
        CountDownLatch latch = new CountDownLatch(all.size());
        List<Throwable> errors = new ArrayList<>();
        AtomicReference<RuleData> reference = new AtomicReference<>();
        for (RuleExecutor executor : all) {
            executor.execute(data)
                    .whenComplete((result, error) -> {
                        if (null != error) {
                            errors.add(error);
                        } else if (executor.getNodeType().isReturnNewValue()) {
                            reference.set(data.newData(result));
                        } else {
                            reference.set(data);
                        }
                        latch.countDown();
                    });
        }
        latch.await(30, TimeUnit.SECONDS);
        if (!CollectionUtils.isEmpty(errors)) {
            if (errors.size() == 1) {
                stage.completeExceptionally(errors.get(0));
            } else {
                stage.completeExceptionally(new MultiException(errors));
            }
        } else {
            stage.complete(reference.get() == null ? data.newData(null) : reference.get());
        }
        return stage;
    }

    @Override
    public boolean should(RuleData data) {
        return condition == null || condition.test(data);
    }
}
