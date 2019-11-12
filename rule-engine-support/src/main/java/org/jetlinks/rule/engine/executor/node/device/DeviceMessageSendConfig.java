package org.jetlinks.rule.engine.executor.node.device;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.message.DeviceMessage;
import org.jetlinks.core.message.MessageType;
import org.jetlinks.rule.engine.api.RuleData;

@Getter
@Setter
public class DeviceMessageSendConfig {

    private MessageType messageType;

    public DeviceMessage convert(String deviceId, RuleData ruleData, boolean async) {
        // TODO: 2019-11-11


        return null;
    }
}
