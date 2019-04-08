package org.jetlinks.rule.engine.api.model;

public interface RuleModuleCodec {

    RuleNodeModel decode(String model,String format);

    String encode(RuleNodeModel model,String format);

}
