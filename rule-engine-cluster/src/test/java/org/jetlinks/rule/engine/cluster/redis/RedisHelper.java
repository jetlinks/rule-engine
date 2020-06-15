package org.jetlinks.rule.engine.cluster.redis;

import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

public class RedisHelper {

    public static ReactiveRedisConnectionFactory connectionFactory(){
        LettuceConnectionFactory factory= new LettuceConnectionFactory(new RedisStandaloneConfiguration());
        factory.afterPropertiesSet();

        return factory;
    }

}
