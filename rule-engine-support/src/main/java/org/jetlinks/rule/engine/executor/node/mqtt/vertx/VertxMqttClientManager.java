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

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public abstract class VertxMqttClientManager implements MqttClientManager {

    private final Map<String, VertxMqttClient> clients = new ConcurrentHashMap<>();

    protected abstract Vertx getVertx();

    protected abstract Mono<VertxMqttConfig> getConfig(String id);

    protected void doClientKeepAlive() {
        for (Map.Entry<String, VertxMqttClient> entry : clients.entrySet()) {
            VertxMqttClient client = entry.getValue();
            if (client.isAlive()) {
                continue;
            }
            log.warn("mqtt client [{}] is disconnected", client.getClient().clientId());
            getConfig(entry.getKey())
                    .filter(VertxMqttConfig::isEnabled)
                    .flatMap(this::createMqttClient)
                    .doOnError(err -> log.warn("reconnect mqtt client [{}] failed ", entry.getKey(), err))
                    .doOnSuccess(newClient -> log.info("reconnect mqtt client [{}] success ", entry.getKey()))
                    .subscribe();
        }
    }

    protected void stopClient(String id) {
        try {
            Optional.ofNullable(clients.get(id))
                    .ifPresent(client -> client.getClient().disconnect());
        } catch (Exception ignore) {

        }
    }

    protected void doClose(VertxMqttClient client) {
        try {
            client.getClient().disconnect();
        } catch (Exception ignore) {

        }
    }

    protected Mono<VertxMqttClient> createMqttClient(VertxMqttConfig config) {
        return Mono.create((sink) -> {

            VertxMqttClient _mqttClient;

            synchronized (clients) {
                _mqttClient = clients.get(config.getId());
                if (_mqttClient != null) {
                    if (_mqttClient.isAlive() || _mqttClient.connecting.get()) {
                        sink.success(_mqttClient);
                        return;
                    }
                }

                if (_mqttClient == null) {
                    _mqttClient = new VertxMqttClient();
                    clients.put(config.getId(), _mqttClient);
                }
                _mqttClient.connecting.set(true);
            }
            if(!config.enabled){
                _mqttClient.connecting.set(false);
                sink.success(_mqttClient);
                return;
            }
            VertxMqttClient mqttClient = _mqttClient;
            io.vertx.mqtt.MqttClient client = io.vertx.mqtt.MqttClient.create(getVertx(), config.options);
            client.exceptionHandler(err -> {
                mqttClient.setLastError(err);
                log.error("mqtt client error", err);
            });

            client.closeHandler(handle -> {
                mqttClient.setLastError(new IOException("mqtt connection closed"));
                log.warn("mqtt connection closed");
            });

            client.connect(config.port, config.host, result -> {
                synchronized (clients) {
                    mqttClient.connecting.set(false);
                    mqttClient.setClient(client);
                    if (result.succeeded()) {
                        log.info("connect mqtt[{} {}:{}] success", config.getId(), config.getHost(), config.getPort());
                    } else {
                        mqttClient.setLastError(result.cause());
                        log.info("connect mqtt[{} {}:{}] error", config.getId(), config.getHost(), config.getPort(), result.cause());
                    }
                    sink.success(mqttClient);
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
        private boolean enabled;
    }
}
