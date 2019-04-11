package org.jetlinks.rule.engine.model;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.jetlinks.rule.engine.model.xml.XmlRuleModelParserStrategy;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class DefaultRuleModelParserTest {

    @Test
    @SneakyThrows
    public void test() {
        DefaultRuleModelParser parser = new DefaultRuleModelParser();
        parser.register(new XmlRuleModelParserStrategy());
        Assert.assertTrue(parser.getAllSupportFormat().contains("re.xml"));
        Assert.assertFalse(parser.getAllSupportFormat().contains("json"));

        ClassPathResource xmlResource = new ClassPathResource("test.re.xml");
        String xml = StreamUtils.copyToString(xmlResource.getInputStream(), StandardCharsets.UTF_8);
        RuleModel model = parser.parse("re.xml", xml);
        Assert.assertNotNull(model);
        try {
            parser.parse("json", "[]");
            Assert.fail();
        } catch (UnsupportedOperationException e) {
            //
        }

    }
}