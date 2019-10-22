package org.jetlinks.rule.engine.standalone;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.events.GlobalNodeEventListener;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Mono;

import java.util.function.Predicate;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface RuleExecutor {

    NodeType getNodeType();

    Mono<Boolean> execute(Publisher<RuleData> publisher);

    void addNext(Predicate<RuleData> condition, RuleExecutor executor);

    void addEventListener(String event, RuleExecutor executor);

    void addEventListener(GlobalNodeEventListener listener);

    void start();

    void stop();
}
