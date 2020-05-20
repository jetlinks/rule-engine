package org.jetlinks.rule.engine.executor;

import org.junit.Test;

import static org.junit.Assert.*;

public class PayloadTypeTest {


    @Test
    public void testHex(){

        assertEquals(PayloadType.HEX.read(PayloadType.HEX.write("1234")),"1234");

        assertEquals(PayloadType.HEX.read(PayloadType.HEX.write("12 34")),"1234");


    }

}