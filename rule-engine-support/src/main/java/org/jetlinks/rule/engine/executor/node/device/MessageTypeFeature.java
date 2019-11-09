package org.jetlinks.rule.engine.executor.node.device;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.rule.engine.api.RuleDataCodec;

@Getter
@AllArgsConstructor
public class MessageTypeFeature implements RuleDataCodec.Feature {
   MessageType messageType;
}
