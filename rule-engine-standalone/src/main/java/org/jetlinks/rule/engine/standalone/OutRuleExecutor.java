package org.jetlinks.rule.engine.standalone;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.jetlinks.rule.engine.api.RuleData;

import java.util.function.Predicate;

@Getter
@Setter
@AllArgsConstructor
public class OutRuleExecutor {
    private Predicate<RuleData> condition;

    private RuleExecutor executor;

    @Override
    public boolean equals(Object obj) {
        if(obj instanceof OutRuleExecutor){
            return ((OutRuleExecutor) obj).getExecutor().equals(executor);
        }
        return super.equals(obj);
    }

    @Override
    public int hashCode() {
        return executor.hashCode();
    }
}
