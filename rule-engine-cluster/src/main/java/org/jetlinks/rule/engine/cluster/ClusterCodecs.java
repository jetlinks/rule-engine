package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.DefaultRuleData;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.codec.Codec;
import org.jetlinks.rule.engine.api.codec.defaults.JsonCodec;

public interface ClusterCodecs {

    Codec<RuleData> ruleDataCodec = JsonCodec.of(DefaultRuleData.class);

}
