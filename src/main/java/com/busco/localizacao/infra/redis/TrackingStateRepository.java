package com.busco.localizacao.infra.redis;

import com.busco.localizacao.domain.entity.TrackingState;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Repository
public class TrackingStateRepository {

    private final ReactiveRedisTemplate<String, TrackingState> redis;

    private static final Duration TTL = Duration.ofMinutes(2);

    public TrackingStateRepository(
            ReactiveRedisTemplate<String, TrackingState> redis
    ) {
        this.redis = redis;
    }

    public Mono<TrackingState> find(String key) {
        return redis.opsForValue().get(key);
    }

    public Mono<Boolean> save(String key, TrackingState state) {
        return redis.opsForValue().set(key, state, TTL);
    }
}