package org.jetlinks.rule.engine.api.task;

import org.jetlinks.rule.engine.api.RuleData;
import reactor.core.publisher.Flux;

/**
 * 数据输入接口
 *
 * @author zhouhao
 * @since 1.0.0
 */
public interface Input {

    Flux<RuleData> accept();
}
