package org.jetlinks.rule.engine.executor.node.mqtt.vertx;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.vertx.core.Vertx;
import io.vertx.core.buffer.Buffer;
import io.vertx.mqtt.MqttClientOptions;
import io.vertx.mqtt.MqttServer;
import io.vertx.mqtt.MqttTopicSubscription;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.core.message.codec.MqttMessage;
import org.junit.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

@Slf4j
public class VertxMqttClientManagerTest {

    public Vertx vertx = Vertx.vertx();

    @Test
    public void test() {
        TestVertxMqttClientManager clientManager = new TestVertxMqttClientManager();

        MqttServer server = MqttServer.create(vertx)
                .endpointHandler(endpoint ->
                        endpoint.subscribeHandler(sb -> {
                            log.info("subscribe :{}", sb.topicSubscriptions());
                            endpoint.subscribeAcknowledge(sb.messageId(), sb.topicSubscriptions().stream()
                                    .map(MqttTopicSubscription::qualityOfService).collect(Collectors.toList()));
                        })
                                .accept(false)
                                .publish("test", Buffer.buffer("test".getBytes()), MqttQoS.AT_MOST_ONCE, false, false))
                .exceptionHandler(err -> log.error(err.getMessage(), err))
                .listen(21883);

        clientManager.getMqttClient("test")
                .flatMapMany(client -> client.subscribe(Arrays.asList("test")))
                .map(MqttMessage::getPayload)
                .take(1)
                .map(r -> r.toString(StandardCharsets.UTF_8))
                .as(StepVerifier::create)
                .expectNext("test")
                .verifyComplete();

        server.close();
    }


    public class TestVertxMqttClientManager extends VertxMqttClientManager {

        @Override
        protected Vertx getVertx() {
            return vertx;
        }

        @Override
        protected Mono<VertxMqttConfig> getConfig(String id) {
            return Mono.just(VertxMqttConfig
                    .builder()
                    .host("127.0.0.1")
                    .id(id)
                    .options(new MqttClientOptions())
                    .port(21883)
                    .build());
        }
    }
}