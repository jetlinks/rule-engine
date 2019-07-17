package org.jetlinks.rule.engine.cluster.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
public class RuleStartEvent implements Serializable {
    private String instanceId;

    private String ruleId;
}
