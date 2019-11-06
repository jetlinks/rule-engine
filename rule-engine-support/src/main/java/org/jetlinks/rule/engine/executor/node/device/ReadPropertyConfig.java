package org.jetlinks.rule.engine.executor.node.device;

import lombok.Getter;
import lombok.Setter;
import org.jetlinks.core.message.property.ReadPropertyMessage;
import org.jetlinks.rule.engine.api.RuleData;

@Getter
@Setter
public class ReadPropertyConfig {

    public ReadPropertyMessage convert(RuleData data, boolean async) {

        return null;
    }

}
