package com.busco.localizacao.infra.rabbit;


import com.busco.localizacao.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.events.ViagemPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.services.TrackingEventPublisher;
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
                        "viagem.posicao",
                        event,
                        message -> {
                            message.getMessageProperties().setHeader("event-type", event.getClass().getSimpleName());
                            message.getMessageProperties().setHeader("version", "1.0");
                            message.getMessageProperties().setHeader("timestamp", System.currentTimeMillis());
                            return message;
                        })

        );
    }

    @Override
    public Mono<Void> publishAluno(
            AlunoPosicaoAtualizadaEvent event
    ) {

        return Mono.fromRunnable(() ->
                rabbit.convertAndSend(
                        "location.exchange",
                        "aluno.posicao",
                        event,
                        message -> {
                            message.getMessageProperties().setHeader("event-type", event.getClass().getSimpleName());
                            message.getMessageProperties().setHeader("version", "1.0");
                            message.getMessageProperties().setHeader("timestamp", System.currentTimeMillis());
                            return message;
                        })
        );
    }
}