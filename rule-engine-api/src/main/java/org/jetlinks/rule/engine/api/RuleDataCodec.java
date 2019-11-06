package org.jetlinks.rule.engine.api;

import reactor.core.publisher.Flux;

import java.util.Arrays;

public interface RuleDataCodec<T> {

    Object encode(T data, Feature... features);

    Flux<T> decode(RuleData data, Feature... features);

    interface Feature {
        String getId();

        String getName();

        default boolean has(Feature... features) {
            return Arrays.stream(features)
                    .anyMatch(feature -> feature.getId().equals(this.getId()));
        }


    }
}
