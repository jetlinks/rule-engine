package org.jetlinks.rule.engine.cluster.logger;

import org.jetlinks.rule.engine.api.Logger;
import org.jetlinks.rule.engine.cluster.Topic;

public class ClusterLogger implements Logger{

    Topic<LogInfo> logInfoTopic;
    Logger parent;

    @Override
    public void info(String message, Object... args) {

    }

    @Override
    public void debug(String message, Object... args) {

    }

    @Override
    public void warn(String message, Object... args) {

    }

    @Override
    public void error(String message, Object... args) {

    }
}
