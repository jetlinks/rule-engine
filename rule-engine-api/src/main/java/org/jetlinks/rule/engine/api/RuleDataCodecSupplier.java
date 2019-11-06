package org.jetlinks.rule.engine.api;

public interface RuleDataCodecSupplier {

    boolean isSupport(Class type);

    RuleDataCodec getCodec();

}
