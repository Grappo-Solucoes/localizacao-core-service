package com.busco.localizacao_core_service.domain.services;

import com.busco.localizacao_core_service.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao_core_service.domain.events.ViagemPosicaoAtualizadaEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class TrackingDomainService {

    private final TrackingEventPublisher publisher;
    private final TrackingEngine trackingEngine;

    public TrackingDomainService(
            TrackingEventPublisher publisher, TrackingEngine trackingEngine
    ) {
        this.publisher = publisher;
        this.trackingEngine = trackingEngine;
    }

    public Mono<Void> processMotorista(
            String viagemId,
            double lat,
            double lng,
            long timestamp
    ) {

        return trackingEngine
                .process(
                        "viagem:" + viagemId,
                        lat,
                        lng,
                        timestamp
                )
                .flatMap(result -> {

                    ViagemPosicaoAtualizadaEvent event =
                            new ViagemPosicaoAtualizadaEvent();

                    event.viagemId = viagemId;
                    event.latitude = result.latitude();
                    event.longitude = result.longitude();
                    event.timestamp = result.timestamp();
                    event.velocidade = result.speed();

                    return publisher.publish(event);
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
                            new AlunoPosicaoAtualizadaEvent();

                    event.alunoId = alunoId;
                    event.viagemId = viagemId;
                    event.latitude = result.latitude();
                    event.longitude = result.longitude();
                    event.timestamp = result.timestamp();

                    return publisher.publishAluno(event);
                });
    }
}