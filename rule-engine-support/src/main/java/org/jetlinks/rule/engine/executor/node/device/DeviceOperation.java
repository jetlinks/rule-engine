package org.jetlinks.rule.engine.executor.node.device;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.hswebframework.web.dict.EnumDict;

@AllArgsConstructor
@Getter
public enum DeviceOperation implements EnumDict<String> {

    ONLINE("上线"),

    OFFLINE("下线"),

    ENCODE("编码消息"),

    DECODE("解码消息"),

    SEND_MESSAGE("发送消息"),

    REPLY_MESSAGE("回复平台消息"),

    HANDLE_MESSAGE("处理平台发往设备的消息");

    private String text;

    @Override
    public String getValue() {
        return name();
    }
}
