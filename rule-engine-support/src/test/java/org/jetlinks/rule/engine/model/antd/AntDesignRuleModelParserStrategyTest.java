package org.jetlinks.rule.engine.model.antd;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class AntDesignRuleModelParserStrategyTest {


    private AntDesignRuleModelParserStrategy strategy=new AntDesignRuleModelParserStrategy();

    @Test
    @SneakyThrows
    public void  test(){
        InputStream data = new ClassPathResource("test.antd.json").getInputStream();

        String json = StreamUtils.copyToString(data, StandardCharsets.UTF_8);
        RuleModel model= strategy.parse(json);

        Assert.assertNotNull(model);
        Assert.assertNotNull(model.getId());
        Assert.assertNotNull(model.getName());


    }

}