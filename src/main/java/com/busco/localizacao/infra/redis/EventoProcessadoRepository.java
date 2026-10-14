package com.busco.localizacao.infra.redis;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Registro dos {@code eventId} ja processados, para tornar o consumo idempotente.
 *
 * <p>O consumo e at-least-once: rebalanceamento e reentrega podem repetir um evento. A consistencia
 * por timestamp cobre a repeticao imediata, mas nao cobre a reentrega depois de o estado de tracking
 * (TTL curto) ter expirado — nesse caso o evento repetido geraria um segundo evento publicado. Por
 * isso a janela aqui e maior que o TTL do estado.</p>
 */
@Repository
public class EventoProcessadoRepository {

    private static final String PREFIXO = "evento:processado:";

    private final ReactiveRedisTemplate<String, String> redis;
    private final Duration ttl;

    public EventoProcessadoRepository(ReactiveRedisTemplate<String, String> eventoProcessadoRedisTemplate,
                                      @Value("${tracking.idempotencia.ttl-minutos:10}") long ttlMinutos) {
        this.redis = eventoProcessadoRedisTemplate;
        this.ttl = Duration.ofMinutes(ttlMinutos);
    }

    /** {@code true} na primeira entrega do evento; {@code false} quando ele ja havia sido registrado. */
    public Mono<Boolean> registrarSeNovo(String eventId) {
        return redis.opsForValue().setIfAbsent(PREFIXO + eventId, "1", ttl);
    }

    /**
     * Libera o registro de um evento cujo processamento falhou.
     *
     * <p>Sem isso a reentrega (o proprio erro handler retenta o registro) encontraria o marcador e
     * seria descartada como duplicata: a falha ficaria silenciosa e o evento nunca chegaria ao dead
     * letter.</p>
     */
    public Mono<Boolean> liberar(String eventId) {
        return redis.opsForValue().delete(PREFIXO + eventId);
    }
}
