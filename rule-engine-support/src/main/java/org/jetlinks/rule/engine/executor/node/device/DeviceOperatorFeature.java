package org.jetlinks.rule.engine.executor.node.device;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.rule.engine.api.RuleDataCodec;

@AllArgsConstructor
public class DeviceOperatorFeature implements RuleDataCodec.Feature {

    @Getter
    private DeviceOperator deviceOperator;

    @Override
    public String getId() {
        return "device-operator";
    }

    @Override
    public String getName() {
        return getId();
    }


}
