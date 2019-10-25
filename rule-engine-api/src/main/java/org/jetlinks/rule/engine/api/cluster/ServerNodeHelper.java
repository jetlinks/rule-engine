package org.jetlinks.rule.engine.api.cluster;

import org.jetlinks.core.cluster.ServerNode;

public class ServerNodeHelper {

    public static boolean isWorker(ServerNode serverNode) {

        return serverNode.hasTag("rule-worker");
    }

    public static boolean isScheduler(ServerNode serverNode) {

        return serverNode.hasTag("rule-scheduler");
    }
}
