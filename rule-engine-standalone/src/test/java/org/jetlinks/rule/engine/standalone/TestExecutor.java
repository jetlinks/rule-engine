package org.jetlinks.rule.engine.standalone;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class TestExecutor {
    public CompletionStage<String> execute(Object data) {

        return CompletableFuture.supplyAsync(() -> String.valueOf(data).toUpperCase());
    }

    public void execute2(Object data) {
        System.out.println(data);
    }

    public void execute3(Object data) {
//        throw new UnsupportedOperationException();
        System.out.println(String.valueOf(data).toLowerCase() + "__");
        throw new RuntimeException();
    }


    public String event1(Object data) {
//        throw new UnsupportedOperationException();
        System.out.println(String.valueOf(data).toLowerCase() + "_event");
        return String.valueOf(data).toLowerCase() + "_";
    }
}
