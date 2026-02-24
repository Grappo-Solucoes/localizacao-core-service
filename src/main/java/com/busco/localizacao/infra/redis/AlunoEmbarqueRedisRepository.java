package com.busco.localizacao.infra.redis;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import org.springframework.data.redis.core.ReactiveHashOperations;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class AlunoEmbarqueRedisRepository {

    private final ReactiveRedisTemplate<String, PontoEmbarque> redisTemplate;
    private final ReactiveHashOperations<String, String, PontoEmbarque> hashOperations;

    private static final Duration TTL_EMBARQUE = Duration.ofHours(12);
    private static final String KEY_PREFIX = "embarque:";

    public AlunoEmbarqueRedisRepository(ReactiveRedisTemplate<String, PontoEmbarque> redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.hashOperations = redisTemplate.opsForHash();
    }

    public AlunoEmbarqueRedisRepository() {
        this.redisTemplate = null;
        this.hashOperations = null;
    }

    /**
     * Salva ponto de embarque de um aluno para uma viagem
     */
    public Mono<Boolean> salvarPontoEmbarque(String viagemId, String alunoId, PontoEmbarque ponto) {
        String key = KEY_PREFIX + viagemId;
        return hashOperations.put(key, alunoId, ponto)
                .flatMap(result -> {
                    if (result) {
                        return redisTemplate.expire(key, TTL_EMBARQUE);
                    }
                    return Mono.just(false);
                });
    }

    /**
     * Busca ponto de embarque de um aluno específico
     */
    public Mono<PontoEmbarque> buscarPontoEmbarque(String viagemId, String alunoId) {
        String key = KEY_PREFIX + viagemId;
        return hashOperations.get(key, alunoId);
    }

    /**
     * Busca todos os pontos de embarque de uma viagem
     */
    public Flux<PontoEmbarque> buscarTodosPontosEmbarque(String viagemId) {
        String key = KEY_PREFIX + viagemId;
        return hashOperations.values(key);
    }

    /**
     * Busca todos os alunos com seus pontos de embarque (Map<alunoId, PontoEmbarque>)
     */
    public Mono<Map<String, PontoEmbarque>> buscarMapaEmbarques(String viagemId) {
        String key = KEY_PREFIX + viagemId;
        return hashOperations.entries(key)
                .collectMap(
                        entry -> entry.getKey().toString(),
                        Map.Entry::getValue
                );
    }

    /**
     * Marca embarque/desembarque como realizado
     */
    public Mono<Boolean> marcarRealizado(String viagemId, String alunoId) {
        return buscarPontoEmbarque(viagemId, alunoId)
                .flatMap(ponto -> {
                    ponto.setRealizado(true);
                    return salvarPontoEmbarque(viagemId, alunoId, ponto);
                });
    }

    /**
     * Remove ponto de embarque (quando aluno já embarcou/desembarcou)
     */
    public Mono<Long> removerPontoEmbarque(String viagemId, String alunoId) {
        String key = KEY_PREFIX + viagemId;
        return hashOperations.remove(key, alunoId);
    }
}