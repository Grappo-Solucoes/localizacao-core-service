package com.busco.localizacao_core_service.domain.services;

import com.busco.localizacao_core_service.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao_core_service.domain.events.ViagemPosicaoAtualizadaEvent;
import reactor.core.publisher.Mono;

public interface TrackingEventPublisher {

    Mono<Void> publish(
            ViagemPosicaoAtualizadaEvent event
    );

    Mono<Void> publishAluno(
            AlunoPosicaoAtualizadaEvent event
    );

}