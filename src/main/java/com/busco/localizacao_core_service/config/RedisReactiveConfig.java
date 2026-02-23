package com.busco.localizacao_core_service.config;


import com.busco.localizacao_core_service.domain.entity.TrackingState;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.*;

@Configuration
public class RedisReactiveConfig {


    @Bean
    public ReactiveRedisTemplate<String, TrackingState> trackingStateRedisTemplate(
            ReactiveRedisConnectionFactory factory,
            ObjectMapper objectMapper
    ) {

        Jackson2JsonRedisSerializer<TrackingState> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, TrackingState.class);

        RedisSerializationContext.RedisSerializationContextBuilder<String, TrackingState> builder =
                RedisSerializationContext.newSerializationContext(new StringRedisSerializer());

        RedisSerializationContext<String, TrackingState> context =
                builder
                        .value(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}