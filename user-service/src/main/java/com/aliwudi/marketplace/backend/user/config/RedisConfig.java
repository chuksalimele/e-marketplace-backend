package com.aliwudi.marketplace.backend.user.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;

import java.util.Map;

@Configuration
public class RedisConfig {

    @Bean
    public ReactiveRedisTemplate<String, String> redisStateTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        RedisSerializationContext<String, String> serializationContext = RedisSerializationContext
                .<String, String>newSerializationContext()
                .key(stringSerializer)
                .value(stringSerializer)
                .hashKey(stringSerializer)
                .hashValue(stringSerializer)
                .build();
        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }

    @Bean
    public ReactiveRedisTemplate<String, Map<String, String>> redisTokenTemplate(ReactiveRedisConnectionFactory factory) {
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        ObjectMapper objectMapper = new ObjectMapper();
        Jackson2JsonRedisSerializer<Map<String, String>> jsonSerializer = new Jackson2JsonRedisSerializer<>(
                objectMapper.getTypeFactory().constructMapType(Map.class, String.class, String.class)
        );
        RedisSerializationContext<String, Map<String, String>> serializationContext = RedisSerializationContext
                .<String, Map<String, String>>newSerializationContext()
                .key(stringSerializer)
                .value(jsonSerializer)
                .hashKey(stringSerializer)
                .hashValue(jsonSerializer)
                .build();
        return new ReactiveRedisTemplate<>(factory, serializationContext);
    }
}