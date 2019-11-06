package org.jetlinks.rule.engine.executor.node.mqtt;


import reactor.core.publisher.Mono;

public interface MqttClientManager {

    Mono<MqttClient> getMqttClient(String id);

}
