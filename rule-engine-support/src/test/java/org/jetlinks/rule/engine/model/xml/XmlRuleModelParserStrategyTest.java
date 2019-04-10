package org.jetlinks.rule.engine.model.xml;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.nio.charset.StandardCharsets;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class XmlRuleModelParserStrategyTest {

    @Test
    @SneakyThrows
    public void testParse() {
        ClassPathResource xmlResource = new ClassPathResource("test.re.xml");
        String xml = StreamUtils.copyToString(xmlResource.getInputStream(), StandardCharsets.UTF_8);
        XmlRuleModelParserStrategy strategy = new XmlRuleModelParserStrategy();

        RuleModel ruleModel = strategy.parse(xml);

        Assert.assertNotNull(ruleModel);
        Assert.assertTrue(ruleModel.getNode("start").isPresent());
        Assert.assertTrue(ruleModel.getNode("to-upper-case").isPresent());
        Assert.assertTrue(ruleModel.getNode("event-done").isPresent());

    }
}