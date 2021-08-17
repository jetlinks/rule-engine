package org.jetlinks.rule.engine.api;

import org.jetlinks.rule.engine.api.task.Input;
import org.jetlinks.rule.engine.api.task.Output;
import org.jetlinks.rule.engine.api.task.Task;

/**
 * 规则引擎钩子,用于对规则引擎相关操作进行全局注入等操作。实现监控等功能。
 *
 * @author zhouhao
 * @since 1.1.8
 */
public class RuleEngineHooks {


    public static Input wrapInput(Input input) {

        return input;
    }

    public static Output wrapOutput(Output input) {

        return input;
    }

    public static Task wrapTask(Task task) {

        return task;
    }

}
