package org.jetlinks.rule.engine.api;


import org.hswebframework.utils.StringUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

public class RuleDataHelper {

    //同步返回执行结果
    public final static String SYNC_RETURN = "sync_return";
    public final static String END_WITH_NODE = "end_with";
    public final static String SYNC_CONTEXT_ID = "sync_context_id";

    //错误信息
    public final static String ERROR_TYPE = "error_type";
    public final static String ERROR_MESSAGE = "error_message";
    public final static String ERROR_STACK = "error_stack";

    //指定启动节点
    public final static String START_WITH_NODE = "start_with";


    public final static String EXECUTE_TIME = "execute_time";

    public RuleData ruleData;

    private RuleDataHelper() {
    }

    public static RuleDataHelper newHelper(RuleData data) {
        RuleDataHelper helper = new RuleDataHelper();
        helper.ruleData = data;
        return helper;
    }

    public RuleData done() {
        return ruleData;
    }

    public RuleDataHelper markStartWith(String startWithNodeId) {
        ruleData.setHeader(START_WITH_NODE, startWithNodeId);
        return this;
    }

    public RuleDataHelper markEndWith(String endWithNodeId) {
        ruleData.setHeader(END_WITH_NODE, endWithNodeId);
        ruleData.setHeader(SYNC_RETURN, true);
        return this;
    }

    public RuleDataHelper whenSync(Consumer<RuleData> consumer) {
        if (isSync(ruleData)) {
            consumer.accept(ruleData);
        }
        return this;
    }

    public static void markStartWith(RuleData data, String startWithNodeId) {
        data.setHeader(START_WITH_NODE, startWithNodeId);
    }

    public static void setExecuteTimeNow(RuleData data) {
        data.setHeader(EXECUTE_TIME, System.currentTimeMillis());
    }

    public static boolean isSync(RuleData data) {
        return data.getHeader(SYNC_RETURN)
                   .map(Boolean.class::cast)
                   .orElse(false);
    }

    public static Optional<String> getStartWithNodeId(RuleData data) {
        return data.getHeader(START_WITH_NODE)
                   .map(String::valueOf);
    }

    public static Optional<String> getEndWithNodeId(RuleData data) {
        return data.getHeader(END_WITH_NODE)
                   .map(String::valueOf);
    }

    public static RuleData markSyncReturn(RuleData data) {
        data.setHeader(SYNC_RETURN, true);

        return data;
    }

    public static boolean hasError(RuleData data) {
        return data.getHeader(ERROR_TYPE).isPresent();
    }

    public static RuleData putError(RuleData data, Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        putError(data, error.getClass().getName(), error.getMessage());
        String stack = StringUtils.throwable2String(error);
        data.setHeader(ERROR_STACK, stack);
        return data;
    }

    public static RuleData putError(RuleData data, String type, String message) {
        data.setHeader(ERROR_TYPE, type);
        data.setHeader(ERROR_MESSAGE, message);
        return data;
    }

    public static RuleData clearError(RuleData data) {
        data.removeHeader(ERROR_TYPE);
        data.removeHeader(ERROR_MESSAGE);
        data.removeHeader(ERROR_STACK);
        return data;
    }

    public static RuleData markSyncReturn(RuleData data, String endWithId) {
        data.setHeader(SYNC_RETURN, true);
        data.setHeader(END_WITH_NODE, endWithId);
        return data;
    }

    @SuppressWarnings("all")
    public static Map<String, Object> toContextMap(RuleData ruleData) {
        Map<String, Object> map = new HashMap<>();
        ruleData.acceptMap(_map -> {
            map.putAll(_map);
        });
        if (map.isEmpty()) {
            map.put("data", ruleData.getData());
        }
        map.compute("headers", (key, value) -> {
            if (value instanceof Map) {
                Map<String, Object> newHeader = new HashMap<>(ruleData.getHeaders());
                newHeader.putAll((Map) value);
                return newHeader;
            }
            return ruleData.getHeaders();
        });
        return map;
    }
}
