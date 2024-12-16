package org.jetlinks.rule.engine.api;

import org.junit.Test;

import static org.junit.Assert.*;

public class RuleConstantsTest {


    @Test
    public void testTopic(){

        assertEquals(
            "/rule-engine/1/2/event/3",
            RuleConstants.Topics.event0("1","2","3").toString()
        );

        assertEquals(
            "/rule-engine/1/2/logger/3",
            RuleConstants.Topics.logger0("1","2","3").toString()
        );

        assertEquals(
            "/rule-engine/1/2/state",
            RuleConstants.Topics.state0("1","2").toString()
        );
    }
}