package org.jetlinks.rule.engine.api;


public abstract class RuleDataHelper {


    private static String SYNC_RETURN = "sync_return";

    private static String SYNC_RETURN_NODE_ID = "sync_return_node_id";

    private static String ERROR_TYPE    = "error_type";
    private static String ERROR_MESSAGE = "error_message";


    private RuleDataHelper() {
    }

    public static boolean isSync(RuleData data) {
        return data.getAttribute(SYNC_RETURN)
                .map(Boolean.class::cast)
                .orElse(false);
    }

    public static String getSyncReturnNodeId(RuleData data) {
        return data.getAttribute(SYNC_RETURN_NODE_ID)
                .map(String::valueOf)
                .orElse(null);
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
        return putError(data, error.getClass().getName(), error.getMessage());
    }

    public static RuleData putError(RuleData data, String type, String message) {
        data.setAttribute(ERROR_TYPE, type);
        data.setAttribute(ERROR_MESSAGE, message);
        return data;
    }

    public static RuleData markSyncReturn(RuleData data, String nodeId) {
        data.setAttribute(SYNC_RETURN, true);
        data.setAttribute(SYNC_RETURN_NODE_ID, nodeId);
        return data;
    }
}
