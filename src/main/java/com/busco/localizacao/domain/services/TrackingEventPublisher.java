package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.events.ViagemPosicaoAtualizadaEvent;
import reactor.core.publisher.Mono;

public interface TrackingEventPublisher {

    Mono<Void> publish(
            ViagemPosicaoAtualizadaEvent event
    );

    Mono<Void> publishAluno(
            AlunoPosicaoAtualizadaEvent event
    );

}