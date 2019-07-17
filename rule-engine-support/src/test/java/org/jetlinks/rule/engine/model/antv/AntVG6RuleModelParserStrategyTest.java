package org.jetlinks.rule.engine.model.antv;

import lombok.SneakyThrows;
import org.jetlinks.rule.engine.api.model.RuleModel;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class AntVG6RuleModelParserStrategyTest {


    private AntVG6RuleModelParserStrategy strategy=new AntVG6RuleModelParserStrategy();

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