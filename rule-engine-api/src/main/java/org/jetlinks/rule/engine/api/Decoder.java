package org.jetlinks.rule.engine.api;

public interface Decoder<T> {

    T decode(Payload payload);

}
