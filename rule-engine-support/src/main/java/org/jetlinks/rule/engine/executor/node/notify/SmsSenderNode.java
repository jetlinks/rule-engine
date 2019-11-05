package org.jetlinks.rule.engine.executor.node.notify;

import lombok.AllArgsConstructor;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@AllArgsConstructor
public class SmsSenderNode extends CommonExecutableRuleNodeFactoryStrategy<SmsNodeConfiguration> {

    private SmsSenderManager smsSenderManager;

    @Override
    public Function<RuleData, Publisher<Object>> createExecutor(ExecutionContext context, SmsNodeConfiguration config) {
        return ruleData -> smsSenderManager
                .getSender(config.getSenderId())
                .flatMap(sender -> {
                    Map<String, Object> emailContext = new HashMap<>();
                    emailContext.put("data", ruleData.getData());
                    emailContext.put("attr", ruleData.getAttributes());
                    return config.send(sender, emailContext);
                });
    }

    @Override
    public String getSupportType() {
        return "sms-sender";
    }
}
