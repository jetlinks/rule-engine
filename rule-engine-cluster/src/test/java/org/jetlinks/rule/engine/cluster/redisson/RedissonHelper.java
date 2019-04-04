package org.jetlinks.rule.engine.cluster.redisson;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.config.Config;

/**
 * @author zhouhao
 * @since 1.0.0
 */
public class RedissonHelper {
    public static RedissonClient newRedissonClient() {
        Config config = new Config();
        config.useSingleServer()
                .setConnectionPoolSize(1024)
                .setSubscriptionConnectionPoolSize(1024)
                .setAddress(System.getProperty("redis.host", "redis://127.0.0.1:6379"))
                .setDatabase(0);

        return Redisson.create(config);
    }

    public static RedissonRxClient newRedissonRxClient() {
        Config config = new Config();
        config.useSingleServer()
                .setConnectionPoolSize(128)
                .setAddress(System.getProperty("redis.host", "redis://127.0.0.1:6379"))
                .setDatabase(0);

        return Redisson.createRx(config);
    }
}
