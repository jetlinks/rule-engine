package org.jetlinks.rule.engine.api.stream;

import org.jetlinks.rule.engine.api.RuleData;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public interface Output {
    void write(RuleData data);
}
