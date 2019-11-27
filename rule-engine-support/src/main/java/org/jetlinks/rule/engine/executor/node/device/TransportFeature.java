package org.jetlinks.rule.engine.executor.node.device;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.core.message.codec.Transport;
import org.jetlinks.rule.engine.api.RuleDataCodec;

@Getter
@AllArgsConstructor
public class TransportFeature implements RuleDataCodec.Feature {
   Transport transport;
}
