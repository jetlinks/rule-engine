package org.jetlinks.rule.engine.cluster.scheduler;

import com.google.common.collect.Maps;
import lombok.SneakyThrows;
import org.jetlinks.core.cluster.ServerNode;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleInstanceContext;
import org.jetlinks.rule.engine.cluster.DefaultWorkerNodeSelector;
import org.jetlinks.rule.engine.cluster.TestApplication;
import org.jetlinks.rule.engine.cluster.persistent.RuleInstancePersistent;
import org.jetlinks.rule.engine.cluster.repository.MockRuleInstanceRepository;
import org.jetlinks.rule.engine.cluster.worker.RuleEngineWorker;
import org.jetlinks.rule.engine.condition.DefaultConditionEvaluator;
import org.jetlinks.rule.engine.condition.supports.DefaultScriptEvaluator;
import org.jetlinks.rule.engine.condition.supports.ScriptConditionEvaluatorStrategy;
import org.jetlinks.rule.engine.executor.DefaultExecutableRuleNodeFactory;
import org.jetlinks.rule.engine.executor.node.JavaMethodInvokeNode;
import org.jetlinks.rule.engine.executor.node.timer.TimerNode;
import org.jetlinks.rule.engine.model.DefaultRuleModelParser;
import org.jetlinks.rule.engine.model.xml.XmlRuleModelParserStrategy;
import org.jetlinks.supports.cluster.redis.RedisClusterManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.util.StreamUtils;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Date;
import java.util.Map;

@SpringBootTest(classes = TestApplication.class)
@RunWith(SpringRunner.class)
public class DefaultRuleEngineSchedulerTest {

    @Autowired
    private ReactiveRedisTemplate<Object, Object> template;

    private RedisClusterManager clusterManager;

    private DefaultRuleEngineScheduler scheduler;

    @Before
    public void init() {
        RuleEngineWorker worker = new RuleEngineWorker();
        Map<String,Object> tags=Maps.newHashMap();
        tags.put("rule-worker","1");
        tags.put("rule-scheduler","1");

        clusterManager = new RedisClusterManager("default",
                ServerNode.builder()
                        .id("test")
                        .tags(tags)
                        .build()
                , template);
        clusterManager.startup();


        DefaultRuleModelParser parser = new DefaultRuleModelParser();
        parser.register(new XmlRuleModelParserStrategy());
        DefaultExecutableRuleNodeFactory nodeFactory = new DefaultExecutableRuleNodeFactory();
        nodeFactory.registerStrategy(new JavaMethodInvokeNode());
        nodeFactory.registerStrategy(new TimerNode(clusterManager));
        scheduler = new DefaultRuleEngineScheduler();
        scheduler.setClusterManager(clusterManager);
        scheduler.setInstanceRepository(new MockRuleInstanceRepository());
        scheduler.setModelParser(parser);
        scheduler.setNodeSelector(new DefaultWorkerNodeSelector());
        DefaultConditionEvaluator evaluator= new DefaultConditionEvaluator();
        evaluator.register(new ScriptConditionEvaluatorStrategy(new DefaultScriptEvaluator()));
        worker.setClusterManager(clusterManager);
        worker.setConditionEvaluator(evaluator);
        worker.setModelParser(parser);
        worker.setNodeFactory(nodeFactory);

        scheduler.start().subscribe();
        worker.start();
    }


    @Test
    @SneakyThrows
    public void testRuleEngine() {
        ClassPathResource resource = new ClassPathResource("test.re.xml");

        String model = StreamUtils.copyToString(resource.getInputStream(), StandardCharsets.UTF_8);

        scheduler.getInstanceRepository()
                .saveInstance(RuleInstancePersistent.builder()
                        .id("test:1.0")
                        .createTime(new Date())
                        .enabled(true)
                        .model(model)
                        .modelFormat("re.xml")
                        .build())
                .subscribe();

        RuleInstanceContext context = scheduler.getInstance("test:1.0").block();
        context.start().block();
        Assert.assertNotNull(context);
        Assert.assertNotNull(context.getId());

        Thread.sleep(2000);
        Flux.range(0, 5)
                .map(i -> RuleData.create("abc" + i))
                .delayElements(Duration.ofSeconds(2))
                .as(context::execute)
                .as(StepVerifier::create)
                .expectNextCount(5)
                .verifyComplete();

        context.stop().block();
    }

}