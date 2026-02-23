package com.busco.localizacao_core_service.infra.rabbit;


import com.busco.localizacao_core_service.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao_core_service.domain.events.ViagemPosicaoAtualizadaEvent;
import com.busco.localizacao_core_service.domain.services.TrackingEventPublisher;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
public class RabbitTrackingEventPublisher
        implements TrackingEventPublisher {

    private final RabbitTemplate rabbit;

    public RabbitTrackingEventPublisher(
            RabbitTemplate rabbit
    ) {
        this.rabbit = rabbit;
    }

    @Override
    public Mono<Void> publish(
            ViagemPosicaoAtualizadaEvent event
    ) {

        return Mono.fromRunnable(() ->
                rabbit.convertAndSend(
                        "location.exchange",
                        "tracking.viagem.posicao",
                        event
                )
        );
    }

    @Override
    public Mono<Void> publishAluno(
            AlunoPosicaoAtualizadaEvent event
    ) {

        return Mono.fromRunnable(() ->
                rabbit.convertAndSend(
                        "location.exchange",
                        "tracking.aluno.posicao",
                        event
                )
        );
    }
}