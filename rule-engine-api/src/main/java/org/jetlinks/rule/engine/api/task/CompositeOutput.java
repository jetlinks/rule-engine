package org.jetlinks.rule.engine.api.task;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.RuleData;
import org.reactivestreams.Publisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

@AllArgsConstructor(staticName = "of")
public class CompositeOutput implements Output {

    private final List<Output> outputs;

    @Override
    public Mono<Boolean> write(RuleData data) {
        if (outputs.size() == 1) {
            return outputs.get(0).write(data);
        }
        return Flux
            .fromIterable(outputs)
            .flatMap(out -> out.write(data))
            .reduce(Boolean::logicalAnd);
    }

    @Override
    public Mono<Boolean> write(Publisher<RuleData> dataStream) {
        return Flux.from(dataStream)
                   .flatMap(data -> Flux.fromIterable(outputs)
                                        .flatMap(out -> out.write(data)))
                   .reduce(Boolean::logicalAnd);
    }

    @Override
    public Mono<Void> write(String nodeId, RuleData data) {
        if (outputs.size() == 1) {
            return outputs.get(0).write(nodeId, data);
        }
        return Flux
            .fromIterable(outputs)
            .flatMap(out -> out.write(nodeId, data))
            .then();
    }

    @Override
    public Mono<Void> write(String nodeId, Publisher<RuleData> dataStream) {
        return Flux.from(dataStream)
                   .flatMap(data -> Flux.fromIterable(outputs)
                                        .flatMap(out -> out.write(nodeId, Mono.just(data))))
                   .then();
    }
}
