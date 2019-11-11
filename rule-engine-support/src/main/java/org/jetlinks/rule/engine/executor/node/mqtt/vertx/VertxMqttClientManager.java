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
            if (client.isAlive() || client.getClient() == null || client.isConnecting()) {
                continue;
            }
            log.warn("mqtt client [{}] is disconnected", client.getClient().clientId());
            getConfig(entry.getKey())
                    .filter(VertxMqttConfig::isEnabled)
                    .flatMap(this::createMqttClient)
                    .doOnError(err -> log.warn("reconnect mqtt client [{}] failed ", entry.getKey(), err))
                    .subscribe();
        }
    }

    protected void stopClient(String id) {
        try {
            Optional.ofNullable(clients.get(id))
                    .ifPresent(this::doClose);
        } catch (Exception ignore) {

        }
    }

    protected void doClose(VertxMqttClient client) {
        try {
            client.connecting.set(false);
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
                    if (_mqttClient.isAlive() || _mqttClient.isConnecting()) {
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
            if (!config.enabled) {
                _mqttClient.connecting.set(false);
                sink.success(_mqttClient);
                return;
            }
            _mqttClient.connectTimeout = config.getOptions().getConnectTimeout();
            _mqttClient.connectTime = System.currentTimeMillis();
            VertxMqttClient mqttClient = _mqttClient;
            io.vertx.mqtt.MqttClient client = io.vertx.mqtt.MqttClient.create(getVertx(), config.options);

            client.exceptionHandler(err -> {
                mqttClient.setLastError(err);
                mqttClient.connecting.set(false);
                log.error("mqtt client error", err);
            });

            client.closeHandler(handle -> {
                mqttClient.connecting.set(false);
                mqttClient.setLastError(new IOException("mqtt connection closed"));
                log.warn("mqtt connection closed");
            });
            client.connect(config.port, config.host, result -> {
                mqttClient.connecting.set(false);
                if (mqttClient.getClient() != client) {
                    doClose(mqttClient);
                }
                mqttClient.setClient(client);
                sink.success(mqttClient);
                if (result.succeeded()) {
                    log.info("connect mqtt[{} {}:{}] success", config.getId(), config.getHost(), config.getPort());
                } else {
                    mqttClient.setLastError(result.cause());
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
        private boolean enabled;
    }
}
