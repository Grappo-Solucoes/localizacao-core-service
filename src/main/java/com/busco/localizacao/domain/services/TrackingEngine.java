package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.TrackingResult;
import com.busco.localizacao.domain.entity.TrackingState;
import com.busco.localizacao.infra.redis.TrackingStateRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TrackingEngine {

    private final TrackingStateRepository repository;
    private final SpeedCalculator speedCalculator;
    private final KalmanFilter kalman;

    public TrackingEngine(
            TrackingStateRepository repository,
            SpeedCalculator speedCalculator,
            KalmanFilter kalman
    ) {
        this.repository = repository;
        this.speedCalculator = speedCalculator;
        this.kalman = kalman;
    }

    public Mono<TrackingResult> process(
            String key,
            double lat,
            double lng,
            long timestamp
    ) {

        return repository
                .find(key)
                .flatMap(state -> processWithState(
                        key,
                        state,
                        lat,
                        lng,
                        timestamp
                ))
                .switchIfEmpty(
                        createFirstState(key, lat, lng, timestamp)
                );
    }

    private Mono<TrackingResult> processWithState(
            String key,
            TrackingState state,
            double lat,
            double lng,
            long timestamp
    ) {

        if (timestamp <= state.getLastTimestamp()) {
            return Mono.empty(); // evento atrasado
        }

        long delta =
                timestamp - state.getLastTimestamp();

        double speed =
                speedCalculator.calculate(
                        state.getLastLat(),
                        state.getLastLng(),
                        lat,
                        lng,
                        delta
                );

        // Anti-spike (GPS bug)
        if (speed > 140) {
            return Mono.empty();
        }

        double smoothLat =
                kalman.smooth(state.getLastLat(), lat);

        double smoothLng =
                kalman.smooth(state.getLastLng(), lng);

        TrackingState newState =
                new TrackingState(
                        smoothLat,
                        smoothLng,
                        timestamp,
                        speed
                );

        return repository
                .save(key, newState)
                .thenReturn(
                        new TrackingResult(
                                smoothLat,
                                smoothLng,
                                speed,
                                timestamp
                        )
                );
    }

    private Mono<TrackingResult> createFirstState(
            String key,
            double lat,
            double lng,
            long timestamp
    ) {

        TrackingState state =
                new TrackingState(
                        lat,
                        lng,
                        timestamp,
                        0
                );

        return repository
                .save(key, state)
                .thenReturn(
                        new TrackingResult(
                                lat,
                                lng,
                                0,
                                timestamp
                        )
                );
    }
}