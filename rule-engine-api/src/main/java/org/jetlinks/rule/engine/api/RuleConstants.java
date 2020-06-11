package org.jetlinks.rule.engine.api;

public interface RuleConstants {

    interface Event {
        String error = "error";
        String result = "result";
        String complete = "complete";
    }

    interface Topics {

        static String prefix(String instanceId, String nodeId) {
            return "/rule/engine/" + instanceId + "/" + nodeId;
        }

        static String input(String instanceId, String nodeId) {
            return prefix(instanceId, nodeId) + "/input";
        }

        static String shutdown(String instanceId, String nodeId) {
            return prefix(instanceId, nodeId) + "/shutdown";
        }

        static String event(String instanceId, String nodeId, String event) {
            return prefix(instanceId, nodeId) + "/event/" + event;
        }
    }
}
