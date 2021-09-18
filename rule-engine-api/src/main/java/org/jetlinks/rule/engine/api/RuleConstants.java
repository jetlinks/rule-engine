package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;

public interface RuleConstants {


    interface Headers {
        /**
         * @see RuleModel#getConfiguration()
         */
        String ruleConfiguration = "ruleConf";

        /**
         * @see RuleNodeModel#getExecutor()
         */
        String jobExecutor = "jobExecutor";

        /**
         * @see RuleModel#getType()
         */
        String modelType = "modelType";
    }

    interface Event {
        String error = "error";
        String result = "result";
        String complete = "complete";

        String start = "start";
        String paused = "paused";

    }

    interface Topics {

        static String prefix(String instanceId, String nodeId) {
            return "/rule-engine/" + instanceId + "/" + nodeId;
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

        static String logger(String instanceId, String nodeId, String level) {
            return prefix(instanceId, nodeId) + "/logger/" + level;
        }


        static String state(String instanceId, String nodeId) {
            return prefix(instanceId, nodeId) + "/state";
        }
    }
}
