package org.jetlinks.rule.engine.api;

import io.opentelemetry.api.common.AttributeKey;
import org.jetlinks.core.lang.SeparatedCharSequence;
import org.jetlinks.core.lang.SharedPathString;
import org.jetlinks.core.trace.FluxTracer;
import org.jetlinks.core.trace.MonoTracer;
import org.jetlinks.core.trace.ReactiveSpanBuilder;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.api.model.RuleNodeModel;
import org.jetlinks.rule.engine.api.scheduler.ScheduleJob;

import java.util.function.BiConsumer;

public interface RuleConstants {

    interface ConfigKey {
        String enableRecorder = "enableRecorder";

    }

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

        SharedPathString templatePrefix = SharedPathString.of("/rule-engine/*/*");
        SharedPathString allEvent = SharedPathString.of("/rule-engine/*/*/event/*");
        SharedPathString allLogger = SharedPathString.of("/rule-engine/*/*/logger/*");

        String allAction = "/rule-engine/*/*/action/**";

        SharedPathString actionPrefix = SharedPathString.of("/rule-engine/*/*/action");


        static SeparatedCharSequence prefix0(String instanceId, String nodeId) {
            return templatePrefix.replace(2, instanceId, 3, nodeId);
        }

        static String prefix(String instanceId, String nodeId) {
            return "/rule-engine/" + instanceId + "/" + nodeId;
        }

        static String input(String instanceId, String nodeId) {
            return prefix(instanceId, nodeId) + "/input";
        }

        static String shutdown(String instanceId, String nodeId) {
            return prefix(instanceId, nodeId) + "/shutdown";
        }


        static CharSequence event0(String instanceId, String nodeId, String event) {
            return allEvent.replace(2, instanceId, 3, nodeId, 5, event);
        }

        static String event(String instanceId, String nodeId, String event) {
            return prefix(instanceId, nodeId) + "/event/" + event;
        }

        static String logger(String instanceId, String nodeId, String level) {
            return prefix(instanceId, nodeId) + "/logger/" + level;
        }

        static CharSequence logger0(String instanceId, String nodeId, String level) {
            return allLogger.replace(2, instanceId, 3, nodeId, 5, level);
        }

        static CharSequence action(String instanceId, String nodeId, CharSequence action) {
            return actionPrefix
                .replace(2, instanceId, 3, nodeId)
                .append(action);
        }


        static String state(String instanceId, String nodeId) {
            return prefix(instanceId, nodeId) + "/state";
        }

        static SeparatedCharSequence state0(String instanceId, String nodeId) {
            return prefix0(instanceId, nodeId).append("state");
        }
    }

    interface Trace {
        AttributeKey<String> instanceId = AttributeKey.stringKey("instanceId");
        AttributeKey<String> name = AttributeKey.stringKey("name");
        AttributeKey<String> nodeId = AttributeKey.stringKey("nodeId");
        AttributeKey<String> executor = AttributeKey.stringKey("executor");
        AttributeKey<String> configuration = AttributeKey.stringKey("configuration");
        AttributeKey<String> model = AttributeKey.stringKey("model");

        static String spanName(String instanceId, String operation) {
            return String.join("/", "/rule-engine", instanceId, operation);
        }

        static String nodeSpanName(String instanceId, String nodeId, String operation) {
            return String.join("/", "/rule-engine", instanceId, nodeId, operation);
        }

        static String reloadNodeSpanName(String instanceId, String nodeId) {
            return nodeSpanName(instanceId, nodeId, "reload");
        }

        static String startNodeSpanName(String instanceId, String nodeId) {
            return nodeSpanName(instanceId, nodeId, "start");
        }

        static String shutdownNodeSpanName(String instanceId, String nodeId) {
            return nodeSpanName(instanceId, nodeId, "shutdown");
        }

        static <T> MonoTracer<T> traceMono(ScheduleJob job, String operation) {
            return traceMono(job, operation, (job1, builder) -> {

            });
        }

        static <T> MonoTracer<T> traceMono(ScheduleJob job, String operation,
                                           BiConsumer<ScheduleJob, ReactiveSpanBuilder> biConsumer) {

            return MonoTracer.create(
                nodeSpanName(job.getInstanceId(), job.getNodeId(), operation),
                builder -> {
                    builder.setAttribute(instanceId, job.getInstanceId());
                    builder.setAttribute(nodeId, job.getNodeId());
                    builder.setAttribute(name, job.getName());
                    biConsumer.accept(job, builder);
                });

        }

        static <T> FluxTracer<T> traceFlux(ScheduleJob job, String operation) {
            return traceFlux(job, operation, (job1, builder) -> {

            });
        }

        static <T> FluxTracer<T> traceFlux(ScheduleJob job, String operation,
                                           BiConsumer<ScheduleJob, ReactiveSpanBuilder> biConsumer) {

            return FluxTracer.create(
                nodeSpanName(job.getInstanceId(), job.getNodeId(), operation),
                builder -> {
                    builder.setAttribute(instanceId, job.getInstanceId());
                    builder.setAttribute(nodeId, job.getNodeId());
                    builder.setAttribute(name, job.getName());
                    biConsumer.accept(job, builder);
                });

        }
    }

    interface Tags {
        String contextId = "_contextId";
    }
}
