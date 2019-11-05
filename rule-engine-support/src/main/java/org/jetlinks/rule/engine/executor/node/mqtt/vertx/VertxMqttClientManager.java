package org.jetlinks.rule.engine.executor.node.mqtt.vertx;

import io.vertx.core.Vertx;
import io.vertx.mqtt.MqttClientOptions;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.jetlinks.rule.engine.executor.node.mqtt.MqttClient;
import org.jetlinks.rule.engine.executor.node.mqtt.MqttClientManager;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class VertxMqttClientManager implements MqttClientManager {

    private final Map<String, VertxMqttClient> clients = new ConcurrentHashMap<>();

    protected abstract Vertx getVertx();

    protected abstract Mono<VertxMqttConfig> getConfig(String id);

    protected void stopClient(String id) {
        Optional.ofNullable(clients.get(id))
                .ifPresent(client -> client.getClient().disconnect());
    }

    protected Mono<VertxMqttClient> createMqttClient(VertxMqttConfig config) {
        return Mono.create((sink) -> {

            synchronized (clients) {
                VertxMqttClient old = clients.get(config.getId());
                if (old != null && old.isAlive()) {
                    sink.success(old);
                    return;
                }
            }
            io.vertx.mqtt.MqttClient client = io.vertx.mqtt.MqttClient.create(getVertx(), config.options);
            client.exceptionHandler(err -> log.error("mqtt client error", err));

            client.connect(config.port, config.host, result -> {
                if (result.succeeded()) {
                    synchronized (clients) {
                        VertxMqttClient mqttClient = clients.get(config.getId());
                        if (mqttClient == null) {
                            clients.put(config.getId(), mqttClient = new VertxMqttClient(client));
                        } else {
                            mqttClient.setClient(client);
                        }
                        sink.success(mqttClient);
                    }
                    log.info("connect mqtt[{} {}:{}] success", config.getId(), config.getHost(), config.getPort());
                } else {
                    sink.error(result.cause());
                    log.info("connect mqtt[{} {}:{}] error", config.getId(), config.getHost(), config.getPort(), result.cause());
                }
            });
        });
    }

    @Override
    public Mono<MqttClient> getMqttClient(String id) {
        return Mono.justOrEmpty(clients.get(id))
                .filter(VertxMqttClient::isAlive)
                .switchIfEmpty(Mono.defer(() -> getConfig(id)
                        .flatMap(this::createMqttClient)))
                .cast(MqttClient.class);
    }

    @Getter
    @Setter
    @Builder
    protected static class VertxMqttConfig {
        private String id;
        private String host;
        private int port;
        private MqttClientOptions options;
    }
}
