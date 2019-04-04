package org.jetlinks.rule.engine.cluster;

import lombok.SneakyThrows;

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

    @SneakyThrows
    public void execute2(Object data) {
        Thread.sleep(50);
        System.out.println(data);
    }

    @SneakyThrows
    public void execute3(Object data) {
//        throw new UnsupportedOperationException();
        System.out.println(String.valueOf(data).toLowerCase() + "_error");
        throw new RuntimeException("error");
    }


    public void event1(Object data) {
//        throw new UnsupportedOperationException();
        System.out.println(String.valueOf(data).toLowerCase() + "_event");
    }
}
