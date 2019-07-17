package org.jetlinks.rule.engine.api.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleInstanceState;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
public class RuleInstanceStateChangedEvent implements Serializable {

    private String schedulerId;

    private String instanceId;

    private RuleInstanceState before;

    private RuleInstanceState after;

}
