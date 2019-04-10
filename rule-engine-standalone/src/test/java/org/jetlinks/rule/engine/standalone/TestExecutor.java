package org.jetlinks.rule.engine.standalone;

import org.hswebframework.utils.StringUtils;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class TestExecutor {
    public CompletionStage<String> appendString(Object data) {

        return CompletableFuture.supplyAsync(() -> String.valueOf(data));
    }

    public String upperCase(String data) {
       return data.toUpperCase();
    }

    public String lowerCase(String data) {
        return data.toLowerCase();
    }

    public String underline(String data) {
        return data.concat("_");
    }

    public void println(String data) {
        System.out.println(data);
    }
}
