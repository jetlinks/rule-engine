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
public class EmailSenderNode extends CommonExecutableRuleNodeFactoryStrategy<EmailNodeConfiguration> {

    private EmailSenderManager emailSenderManager;


    @Override
    public Function<RuleData, Publisher<Object>> createExecutor(ExecutionContext context, EmailNodeConfiguration config) {


        return ruleData -> emailSenderManager
                .getSender(config.getSenderId())
                .flatMap(emailSender -> {
                    Map<String, Object> emailContext = new HashMap<>();
                    emailContext.put("data", ruleData.getData());
                    emailContext.put("attr", ruleData.getAttributes());

                    return config.send(emailSender, emailContext);

                });
    }

    @Override
    public String getSupportType() {
        return "email-sender";
    }
}
