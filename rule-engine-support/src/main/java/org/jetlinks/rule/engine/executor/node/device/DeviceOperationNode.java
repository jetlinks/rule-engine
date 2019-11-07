package org.jetlinks.rule.engine.executor.node.device;

import lombok.Setter;
import org.jetlinks.core.cluster.ClusterManager;
import org.jetlinks.core.device.DeviceOperator;
import org.jetlinks.core.device.DeviceRegistry;
import org.jetlinks.core.message.Message;
import org.jetlinks.core.message.codec.EncodedMessage;
import org.jetlinks.core.message.codec.MessageDecodeContext;
import org.jetlinks.core.message.codec.MessageEncodeContext;
import org.jetlinks.core.server.MessageHandler;
import org.jetlinks.rule.engine.api.RuleData;
import org.jetlinks.rule.engine.api.RuleDataCodecs;
import org.jetlinks.rule.engine.api.executor.ExecutionContext;
import org.jetlinks.rule.engine.executor.CommonExecutableRuleNodeFactoryStrategy;
import org.reactivestreams.Publisher;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;
import java.util.function.Function;

import static org.jetlinks.rule.engine.executor.node.device.DeviceOperation.HANDLE_MESSAGE;

public class DeviceOperationNode extends CommonExecutableRuleNodeFactoryStrategy<DeviceOperationConfiguration> {

    private MessageHandler messageHandler;

    private ClusterManager clusterManager;

    private DeviceRegistry registry;

    @Setter
    private Consumer<DeviceOperator> onOnline;

    @Setter
    private Consumer<DeviceOperator> onOffline;

    public DeviceOperationNode(MessageHandler messageHandler, ClusterManager clusterManager, DeviceRegistry deviceRegistry) {
        this.messageHandler = messageHandler;
        this.clusterManager = clusterManager;
        this.registry = deviceRegistry;
    }

    public DeviceOperationNode onOnline(Consumer<DeviceOperator> onOnline) {
        this.onOnline = onOnline;

        return this;
    }

    public DeviceOperationNode onOffline(Consumer<DeviceOperator> onOffline) {
        this.onOffline = onOffline;
        return this;
    }

    @Override
    public Function<RuleData, ? extends Publisher<?>> createExecutor(ExecutionContext context,
                                                                     DeviceOperationConfiguration config) {

        return ruleData -> {
            String deviceId = config.getDeviceId(ruleData);
            if (deviceId == null) {
                context.logger().warn("无法从上游数据中获取deviceId:{} :{}", config.getDeviceId(), ruleData);
                return Mono.empty();
            }
            return registry
                    .getDevice(deviceId)
                    .switchIfEmpty(Mono.fromRunnable(() -> context.logger().warn("设备[{}]未注册到注册中心", deviceId)))
                    .flatMapMany(operator -> handleMessage(config, operator, context, ruleData));

        };
    }

    protected Publisher<?> handleMessage(DeviceOperationConfiguration configuration,
                                         DeviceOperator operator,
                                         ExecutionContext context,
                                         RuleData ruleData) {

        switch (configuration.getOperation()) {
            case ONLINE:
                String serverId = "rule:".concat(context.getInstanceId()).concat(clusterManager.getCurrentServerId());
                return operator
                        .online(serverId, serverId)
                        .thenReturn(ruleData)
                        .doFinally(r -> {
                            if (onOnline != null) {
                                onOnline.accept(operator);
                            }
                        });
            case OFFLINE:
                return operator
                        .offline()
                        .thenReturn(ruleData)
                        .doFinally(r -> {
                            if (onOffline != null) {
                                onOffline.accept(operator);
                            }
                        });
            case DECODE:
                return operator.getProtocol()
                        .flatMap(protocol -> protocol.getMessageCodec(configuration.getTransport()))
                        .flatMapMany(codec -> configuration
                                .createEncodedMessage(ruleData)
                                .flatMap(msg -> codec.decode(new MessageDecodeContext() {
                                    @Override
                                    public EncodedMessage getMessage() {
                                        return msg;
                                    }

                                    @Override
                                    public DeviceOperator getDevice() {
                                        return operator;
                                    }
                                })))
                        .map(this::convertRuleSafe);
            case ENCODE:
                return operator.getProtocol()
                        .flatMap(protocol -> protocol.getMessageCodec(configuration.getTransport()))
                        .flatMapMany(codec -> configuration
                                .createDecodedMessage(ruleData)
                                .flatMap(msg -> codec.encode(new MessageEncodeContext() {
                                    @Override
                                    public Message getMessage() {
                                        return msg;
                                    }

                                    @Override
                                    public DeviceOperator getDevice() {
                                        return operator;
                                    }
                                }))).map(this::convertRuleSafe);
            case SEND_MESSAGE:
                return configuration.doSendMessage(operator, ruleData);
            default:
                return Mono.just(ruleData);
        }
    }

    protected Object convertRuleSafe(EncodedMessage message) {
        return RuleDataCodecs.getCodec(message.getClass())
                .map(codec -> codec.encode(message))
                .orElse(message);
    }

    protected Object convertRuleSafe(Message message) {

        return RuleDataCodecs.getCodec(message.getClass())
                .map(codec -> codec.encode(message))
                .orElse(message);
    }

    protected RuleData convertDeviceMessage(Message message) {
        return RuleData.create(convertRuleSafe(message));
    }

    @Override
    protected void onStarted(ExecutionContext context, DeviceOperationConfiguration config) {
        //桥接发往设备的消息
        if (config.getOperation() == HANDLE_MESSAGE) {
            Disposable disposable = messageHandler
                    .handleSendToDeviceMessage("rule:".concat(context.getInstanceId()).concat(clusterManager.getCurrentServerId()))
                    .map(this::convertDeviceMessage)
                    .doOnNext(ruleData -> context.logger().info("桥接发往设备的消息:{}", ruleData))
                    .flatMap(ruleData -> context.getOutput()
                            .write(Mono.just(ruleData)
                                    .doOnError(err -> context.onError(ruleData, err).subscribe())))
                    .onErrorContinue((err, data) -> context.onError(RuleData.create(data), err).subscribe())
                    .subscribe(success -> {

                    });

            context.onStop(disposable::dispose);
        }
    }


    @Override
    public String getSupportType() {
        return "device-operation";
    }
}
