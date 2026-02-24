package com.busco.localizacao.infra.redis;

import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.entity.RotaViagem;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import java.time.Duration;
import java.util.List;

@Repository
public class RotaRedisRepository {

    private final ReactiveRedisTemplate<String, RotaViagem> redisRota;
    private final ReactiveRedisTemplate<String, List<PontoRota>> redisPontos;

    private static final Duration TTL_ROTA = Duration.ofHours(12); // Rota do dia
    private static final String KEY_PREFIX = "rota:";

    public RotaRedisRepository(
            ReactiveRedisTemplate<String, RotaViagem> redisRota,
            ReactiveRedisTemplate<String, List<PontoRota>> redisPontos) {
        this.redisRota = redisRota;
        this.redisPontos = redisPontos;
    }

    public RotaRedisRepository() {
        this.redisRota = null;
        this.redisPontos = null;
    }

    /**
     * Salva rota completa da viagem
     */
    public Mono<Boolean> salvarRota(String viagemId, RotaViagem rota) {
        String key = KEY_PREFIX + viagemId;
        return redisRota.opsForValue().set(key, rota, TTL_ROTA);
    }

    /**
     * Busca rota da viagem
     */
    public Mono<RotaViagem> buscarRota(String viagemId) {
        String key = KEY_PREFIX + viagemId;
        return redisRota.opsForValue().get(key);
    }

    /**
     * Salva lista de pontos da rota
     */
    public Mono<Boolean> salvarPontosRota(String viagemId, List<PontoRota> pontos) {
        String key = KEY_PREFIX + viagemId + ":pontos";
        return redisPontos.opsForValue().set(key, pontos, TTL_ROTA);
    }

    /**
     * Busca pontos da rota
     */
    public Mono<List<PontoRota>> buscarPontosRota(String viagemId) {
        String key = KEY_PREFIX + viagemId + ":pontos";
        return redisPontos.opsForValue().get(key);
    }

    /**
     * Atualiza um ponto específico (ex: marcar como visitado)
     */
    public Mono<Boolean> atualizarPonto(String viagemId, PontoRota pontoAtualizado) {
        return buscarPontosRota(viagemId)
                .flatMap(pontos -> {
                    for (int i = 0; i < pontos.size(); i++) {
                        if (pontos.get(i).getId().equals(pontoAtualizado.getId())) {
                            pontos.set(i, pontoAtualizado);
                            break;
                        }
                    }
                    return salvarPontosRota(viagemId, pontos);
                });
    }
}