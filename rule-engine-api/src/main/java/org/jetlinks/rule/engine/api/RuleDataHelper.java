package org.jetlinks.rule.engine.api;


import org.hswebframework.utils.StringUtils;

import java.util.Optional;
import java.util.function.Consumer;

public class RuleDataHelper {

    //同步返回执行结果
    public final static String SYNC_RETURN   = "sync_return";
    public final static String END_WITH_NODE = "end_with";

    //错误信息
    public final static String ERROR_TYPE    = "error_type";
    public final static String ERROR_MESSAGE = "error_message";
    public final static String ERROR_STACK   = "error_stack";

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
        ruleData.setAttribute(START_WITH_NODE, startWithNodeId);
        return this;
    }

    public RuleDataHelper markEndWith(String endWithNodeId) {
        ruleData.setAttribute(END_WITH_NODE, endWithNodeId);
        ruleData.setAttribute(SYNC_RETURN, true);
        return this;
    }

    public RuleDataHelper whenSync(Consumer<RuleData> consumer) {
        if (isSync(ruleData)) {
            consumer.accept(ruleData);
        }
        return this;
    }

    public static void markStartWith(RuleData data, String startWithNodeId) {
        data.setAttribute(START_WITH_NODE, startWithNodeId);
    }

    public static void setExecuteTimeNow(RuleData data) {
        data.setAttribute(EXECUTE_TIME, System.currentTimeMillis());
    }

    public static boolean isSync(RuleData data) {
        return data.getAttribute(SYNC_RETURN)
                .map(Boolean.class::cast)
                .orElse(false);
    }

    public static Optional<String> getStartWithNodeId(RuleData data) {
        return data.getAttribute(START_WITH_NODE)
                .map(String::valueOf);
    }

    public static Optional<String> getEndWithNodeId(RuleData data) {
        return data.getAttribute(END_WITH_NODE)
                .map(String::valueOf);
    }

    public static RuleData markSyncReturn(RuleData data) {
        data.setAttribute(SYNC_RETURN, true);

        return data;
    }

    public static boolean hasError(RuleData data) {
        return data.getAttribute(ERROR_TYPE).isPresent();
    }

    public static RuleData putError(RuleData data, Throwable error) {
        while (error.getCause() != null) {
            error = error.getCause();
        }
        putError(data, error.getClass().getName(), error.getMessage());
        String stack = StringUtils.throwable2String(error);
        data.setAttribute(ERROR_STACK, stack);
        return data;
    }

    public static RuleData putError(RuleData data, String type, String message) {
        data.setAttribute(ERROR_TYPE, type);
        data.setAttribute(ERROR_MESSAGE, message);
        return data;
    }

    public static RuleData clearError(RuleData data) {
        data.removeAttribute(ERROR_TYPE);
        data.removeAttribute(ERROR_MESSAGE);
        data.removeAttribute(ERROR_STACK);
        return data;
    }

    public static RuleData markSyncReturn(RuleData data, String nodeId) {
        data.setAttribute(SYNC_RETURN, true);
        data.setAttribute(END_WITH_NODE, nodeId);
        return data;
    }
}
