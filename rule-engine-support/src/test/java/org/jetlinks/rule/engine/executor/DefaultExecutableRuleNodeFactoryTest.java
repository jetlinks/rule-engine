package org.jetlinks.rule.engine.executor;

import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.executor.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicLong;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultExecutableRuleNodeFactoryTest {

    @Test
    public void test() {
        DefaultExecutableRuleNodeFactory factory = new DefaultExecutableRuleNodeFactory();
        factory.registerStrategy(new ExecutableRuleNodeFactoryStrategy() {
            @Override
            public String getSupportType() {
                return "test";
            }

            @Override
            public ExecutableRuleNode create(RuleNodeConfiguration configuration) {
                return (executionContext -> {
                    for (int i = 0; i < 100; i++) {
                        executionContext.getOutput().write(RuleData.create(i));
                    }
                });
            }
        });

        Assert.assertTrue(factory.getAllSupportExecutor().contains("test"));
        {

            RuleNodeConfiguration configuration = new RuleNodeConfiguration();
            configuration.setId("test");
            configuration.setExecutor("test");
            ExecutableRuleNode ruleNode = factory.create(configuration);
            Assert.assertNotNull(ruleNode);
            AtomicLong counter = new AtomicLong();
            ruleNode.start(new ExecutionContext() {
                @Override
                public String getInstanceId() {
                    return "test";
                }

                @Override
                public String getNodeId() {
                    return "test";
                }

                @Override
                public Logger logger() {
                    return new Slf4jLogger("test");
                }

                @Override
                public Input getInput() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public Output getOutput() {
                    return data -> counter.incrementAndGet();
                }

                @Override
                public void fireEvent(String event, RuleData data) {

                }

                @Override
                public void onError(RuleData data, Throwable e) {

                }

                @Override
                public void stop() {

                }

                @Override
                public void onStop(Runnable runnable) {

                }
            });

            Assert.assertEquals(counter.get(), 100);
        }

        try {
            RuleNodeConfiguration configuration = new RuleNodeConfiguration();
            configuration.setId("test2");
            configuration.setExecutor("email");
            factory.create(configuration);
            Assert.fail();
        } catch (UnsupportedOperationException e) {

        }
    }
}