package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.RouteInfo;
import com.busco.localizacao.domain.entity.StopInfo;
import com.busco.localizacao.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.events.ViagemPosicaoAtualizadaEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TrackingDomainService {

    private final TrackingEventPublisher publisher;
    private final TrackingEngine trackingEngine;
    private final GeocodingService geocodingService;
    private final RouteCalculator routeCalculator;
    private final GeofencingService geofencingService;
    private final StopDetectionService stopDetectionService;
    private final RoutePredictionService predictionService;

    public TrackingDomainService(
            TrackingEventPublisher publisher, TrackingEngine trackingEngine, GeocodingService geocodingService, RouteCalculator routeCalculator, GeofencingService geofencingService, StopDetectionService stopDetectionService, RoutePredictionService predictionService
    ) {
        this.publisher = publisher;
        this.trackingEngine = trackingEngine;
        this.geocodingService = geocodingService;
        this.routeCalculator = routeCalculator;
        this.geofencingService = geofencingService;
        this.stopDetectionService = stopDetectionService;
        this.predictionService = predictionService;
    }

    public Mono<Void> processMotorista(
            String viagemId,
            double lat,
            double lng,
            long timestamp,
            Double destinoLat,
            Double destinoLng,
            Double batteryLevel
    ) {

        return trackingEngine
                .process(
                        "viagem:" + viagemId,
                        lat,
                        lng,
                        timestamp
                )
                .flatMap(result -> {

                    ViagemPosicaoAtualizadaEvent.Builder eventBuilder =
                            new ViagemPosicaoAtualizadaEvent.Builder()
                                    .viagemId(viagemId)
                                    .latitude(result.latitude())
                                    .longitude(result.longitude())
                                    .timestamp(result.timestamp())
                                    .velocidade(result.speed());

                    return Mono.zip(
                            geocodingService.reverseGeocode(lat, lng),
                            destinoLat != null && destinoLng != null ?
                                    routeCalculator.calculateETA(lat, lng, destinoLat, destinoLng, result.speed()) :
                                    Mono.just(new RouteInfo(0, 0)),
                            predictionService.predictNextPosition(viagemId)
                                    .map(Mono::just)
                                    .orElse(Mono.empty())
                    ).flatMap(tuple -> {
                        eventBuilder.endereco(tuple.getT1())
                                .distanciaParaDestino(tuple.getT2().distance())
                                .etaSegundos(tuple.getT2().etaSeconds());

                        // Detecta paradas
                        StopInfo stop = stopDetectionService.checkStop(
                                viagemId, result.speed(), timestamp
                        );

                        if (stop != null) {
                            eventBuilder.status("PARADO");
                        }

                        return publisher.publish(eventBuilder.build());
                    });

                });
    }

    public Mono<Void> processAluno(
            String alunoId,
            String viagemId,
            double lat,
            double lng,
            long timestamp
    ) {

        return trackingEngine
                .process(
                        "aluno:" + alunoId,
                        lat,
                        lng,
                        timestamp
                )
                .flatMap(result -> {

                    AlunoPosicaoAtualizadaEvent event =
                            new AlunoPosicaoAtualizadaEvent.Builder()
                                    .alunoId(alunoId)
                                    .viagemId(viagemId)
                                    .latitude(result.latitude())
                                    .longitude(result.longitude())
                                    .timestamp(result.timestamp())
                                    .build();

                    return publisher.publishAluno(event);
                });
    }
}