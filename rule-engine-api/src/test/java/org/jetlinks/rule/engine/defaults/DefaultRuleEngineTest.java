package org.jetlinks.rule.engine.defaults;

import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.model.RuleLink;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.junit.Assert;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.concurrent.atomic.AtomicLong;

public class DefaultRuleEngineTest {


    @Test
    public void test() {

        LocalScheduler scheduler = new LocalScheduler("local");

        LocalWorker worker = new LocalWorker("local", "Local", "localhost", new LocalEventBus(), (c, d) -> true);

        worker.addExecutor(new MockTaskExecutorProvider("createWorld", ruleData -> Mono.just(ruleData.newData("world"))));

        AtomicLong counter = new AtomicLong();

        worker.addExecutor(new MockTaskExecutorProvider("createBoom", ruleData -> {
            counter.incrementAndGet();
            return Mono.just(ruleData.newData("boom"));
        }));

        scheduler.addWorker(worker);

        DefaultRuleEngine engine = new DefaultRuleEngine(scheduler);

        RuleModel model = new RuleModel();
        model.setId("test");
        model.setName("测试模型");

        {
            RuleNodeModel node1 = new RuleNodeModel();
            node1.setId("createWorld");
            node1.setName("测试节点");
            node1.setExecutor("createWorld");


            RuleNodeModel node2 = new RuleNodeModel();
            node2.setId("createBoom");
            node2.setName("测试节点2");
            node2.setExecutor("createBoom");

            RuleLink link = new RuleLink();
            link.setSource(node1);
            link.setTarget(node2);
            link.setId("1-2");

            node1.getOutputs().add(link);

            node2.getInputs().add(link);

            model.getNodes().add(node1);
            model.getNodes().add(node2);

        }

        engine.startRule("test", model)
                .as(StepVerifier::create)
                .expectNextCount(2)
                .verifyComplete();

        engine.getTasks("test")
                .filter(task->task.getJob().getNodeId().equals("createWorld"))
                .take(1)
                .flatMap(task -> task.execute(Mono.just(RuleData.create("test"))))
                .as(StepVerifier::create)
                .expectComplete()
                .verify();

        Assert.assertEquals(counter.get(),1);

    }

}