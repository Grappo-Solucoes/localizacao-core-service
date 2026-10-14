package com.busco.localizacao.config;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.entity.TrackingState;
import com.busco.localizacao.domain.vo.GeoPoint;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.List;

/**
 * Templates Redis do serviço.
 *
 * <p>O mapeamento JSON do estado em Redis é responsabilidade desta camada, por isso os
 * ajustes de serialização entram como <b>mixins Jackson</b> (`objectMapper.copy()` +
 * `addMixIn`) em vez de anotações nas entidades de domínio, preservando D2.
 *
 * <p>Os mixins existem porque `PontoRota` e `PontoEmbarque` têm <b>dois</b> construtores
 * de 4-5 argumentos e nenhum sem argumentos, então o Jackson não tinha como escolher um
 * criador implícito e toda leitura estourava com "no Creators, like default constructor,
 * exist". O criador explícito com os nomes das propriedades resolve; os demais campos
 * chegam pelos setters. `TrackingState` não precisa de mixin: tem um único construtor, e
 * o criador implícito por nomes de parâmetro funciona (verificado removendo o mixin: o
 * round trip continua passando). `ignoreUnknown = true` mantém a leitura tolerante a
 * campos extras de quem escreve nessas mesmas chaves.
 */
@Configuration
public class RedisReactiveConfig {

    private final ObjectMapper redisObjectMapper;

    public RedisReactiveConfig(ObjectMapper objectMapper) {
        this.redisObjectMapper = objectMapper.copy()
                .addMixIn(PontoRota.class, PontoRotaMixin.class)
                .addMixIn(PontoEmbarque.class, PontoEmbarqueMixin.class);
    }

    @Bean
    public ReactiveRedisTemplate<String, TrackingState> trackingStateRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        Jackson2JsonRedisSerializer<TrackingState> serializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper, TrackingState.class);

        RedisSerializationContext<String, TrackingState> context =
                RedisSerializationContext
                        .<String, TrackingState>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    @Bean
    public ReactiveRedisTemplate<String, List<PontoRota>> pontosRotaRedisTemplate(
            ReactiveRedisConnectionFactory factory) {

        Jackson2JsonRedisSerializer<List<PontoRota>> serializer =
                new Jackson2JsonRedisSerializer<>(redisObjectMapper,
                        redisObjectMapper.getTypeFactory().constructCollectionType(List.class, PontoRota.class));

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
                new Jackson2JsonRedisSerializer<>(redisObjectMapper, PontoEmbarque.class);

        // AlunoEmbarqueRedisRepository usa operacoes de HASH
        // (embarque:{viagemId} -> campo alunoId -> PontoEmbarque). Sem hashValue o
        // template usa o writer padrao, que so aceita byte[] e lancava
        // "Cannot serialize value of type PontoEmbarque without a serializer" em toda
        // escrita de embarque.
        RedisSerializationContext<String, PontoEmbarque> context =
                RedisSerializationContext
                        .<String, PontoEmbarque>newSerializationContext(new StringRedisSerializer())
                        .value(serializer)
                        .hashKey(new StringRedisSerializer())
                        .hashValue(serializer)
                        .build();

        return new ReactiveRedisTemplate<>(factory, context);
    }

    // ---------------------------------------------------------------------
    // Mixins: descrevem o mapeamento JSON das entidades de domínio para o Redis.
    // ---------------------------------------------------------------------

    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class PontoRotaMixin {

        @JsonCreator
        PontoRotaMixin(
                @JsonProperty("id") String id,
                @JsonProperty("nome") String nome,
                @JsonProperty("localizacao") GeoPoint localizacao,
                @JsonProperty("tipo") PontoRota.TipoPonto tipo) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    abstract static class PontoEmbarqueMixin {

        @JsonCreator
        PontoEmbarqueMixin(
                @JsonProperty("alunoId") String alunoId,
                @JsonProperty("pontoId") String pontoId,
                @JsonProperty("localizacao") GeoPoint localizacao,
                @JsonProperty("tipo") PontoEmbarque.TipoEmbarque tipo) {
        }
    }
}
