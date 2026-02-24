package com.busco.localizacao.config;

import com.busco.localizacao.domain.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

@Configuration
public class RedisReactiveConfig {

    private final ObjectMapper objectMapper;

    public RedisReactiveConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public ReactiveRedisTemplate<String, TrackingState> trackingStateRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        Jackson2JsonRedisSerializer<TrackingState> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, TrackingState.class);

        RedisSerializationContext<String, TrackingState> context =
                RedisSerializationContext
                        .<String, TrackingState>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, RotaViagem> rotaViagemRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        Jackson2JsonRedisSerializer<RotaViagem> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, RotaViagem.class);

        RedisSerializationContext<String, RotaViagem> context =
                RedisSerializationContext
                        .<String, RotaViagem>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, List<PontoRota>> pontosRotaRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        Jackson2JsonRedisSerializer<List<PontoRota>> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PontoRota.class));

        RedisSerializationContext<String, List<PontoRota>> context =
                RedisSerializationContext
                        .<String, List<PontoRota>>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, PontoEmbarque> alunoEmbarqueRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        Jackson2JsonRedisSerializer<PontoEmbarque> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper, PontoEmbarque.class);

        RedisSerializationContext<String, PontoEmbarque> context =
                RedisSerializationContext
                        .<String, PontoEmbarque>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    // Se você precisar de uma lista de PontoEmbarque
    @Bean
    public ReactiveRedisTemplate<String, List<PontoEmbarque>> alunoEmbarqueListRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        Jackson2JsonRedisSerializer<List<PontoEmbarque>> serializer =
                new Jackson2JsonRedisSerializer<>(objectMapper,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, PontoEmbarque.class));

        RedisSerializationContext<String, List<PontoEmbarque>> context =
                RedisSerializationContext
                        .<String, List<PontoEmbarque>>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }
}