package org.jetlinks.rule.engine.cluster;

import org.jetlinks.rule.engine.api.RuleData;

public abstract class RuleDataHelper {

    private RuleDataHelper(){}

    public static boolean isSync(RuleData data) {
        return data.getAttribute("sync_return")
                .map(Boolean.class::cast)
                .orElse(false);
    }

    public static String getSyncReturnNodeId(RuleData data) {
        return data.getAttribute("sync_return_node_id")
                .map(String::valueOf)
                .orElse(null);
    }

    public static void setSync(RuleData data) {
        data.setAttribute("sync_return", true);
    }

    public static void setSyncReturnNodeId(RuleData data, String nodeId) {
        data.setAttribute("sync_return_node_id", nodeId);
    }
}
