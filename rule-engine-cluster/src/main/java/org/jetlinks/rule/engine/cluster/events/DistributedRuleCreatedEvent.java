package org.jetlinks.rule.engine.cluster.events;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor(staticName = "of")
@NoArgsConstructor
public class DistributedRuleCreatedEvent {

   private List<DistributedRuleDetail> details;

}
