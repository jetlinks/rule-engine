package org.jetlinks.rule.engine.executor.supports;

import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.Slf4jLogger;
import org.jetlinks.rule.engine.api.executor.*;
import org.jetlinks.rule.engine.api.model.NodeType;
import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class JavaMethodInvokeStrategyTest {

    @Test
    public void test() {
        RuleNodeConfiguration configuration = new RuleNodeConfiguration();
        configuration.setId("test");
        configuration.setExecutor("java-method");
        configuration.setNodeType(NodeType.MAP);
        configuration.setConfiguration(new HashMap<>());
        configuration.getConfiguration().put("className",JavaMethodInvokeStrategyTest.class.getName());
        configuration.getConfiguration().put("methodName","test2");

        JavaMethodInvokeStrategy strategy = new JavaMethodInvokeStrategy();
        ExecutableRuleNode node = strategy.create(configuration);
        AtomicReference<RuleData> reference=new AtomicReference<>();

        node.start(new ExecutionContext() {
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
                return new Input() {

                    @Override
                    public boolean accept(Consumer<RuleData> accept) {
                        accept.accept(RuleData.create("test"));
                        return false;
                    }

                    @Override
                    public void close() {

                    }
                };
            }

            @Override
            public Output getOutput() {
                return reference::set;
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

        Assert.assertNotNull(reference.get());
        Assert.assertNotNull(reference.get().getData());
        Assert.assertEquals(reference.get().getData(),"test_");


    }

    public String test2(String x) {
        return x + "_";
    }
}