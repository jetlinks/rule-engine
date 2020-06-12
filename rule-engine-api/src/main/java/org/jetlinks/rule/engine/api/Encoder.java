package org.jetlinks.rule.engine.api;

public interface Encoder<T> {

    Payload encode(T body);

}
