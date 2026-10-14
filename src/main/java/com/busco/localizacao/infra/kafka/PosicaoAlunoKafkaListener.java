package com.busco.localizacao.infra.kafka;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.redis.EventoProcessadoRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Entrada do stream de posicoes de aluno, o caminho que alimenta distancia ate o onibus e aptidao
 * para embarque no pipeline.
 */
@Component
public class PosicaoAlunoKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PosicaoAlunoKafkaListener.class);

    private final TrackingOrchestratorService orchestrator;
    private final EventoProcessadoRepository eventosProcessados;
    private final Duration tempoLimite;

    public PosicaoAlunoKafkaListener(TrackingOrchestratorService orchestrator,
                                     EventoProcessadoRepository eventosProcessados,
                                     @Value("${tracking.tempo-limite-processamento-ms:15000}") long tempoLimiteMs) {
        this.orchestrator = orchestrator;
        this.eventosProcessados = eventosProcessados;
        this.tempoLimite = Duration.ofMillis(tempoLimiteMs);
    }

    @KafkaListener(topics = "${tracking.topico-aluno}", groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.busco.localizacao.infra.kafka.PosicaoAlunoEvento")
    public void consumir(ConsumerRecord<String, PosicaoAlunoEvento> registro, Acknowledgment ack) {
        PosicaoAlunoEvento evento = registro.value();
        if (invalido(evento)) {
            log.warn("Posicao do aluno descartada (topico {}, offset {}): evento fora do contrato",
                    registro.topic(), registro.offset());
            ack.acknowledge();
            return;
        }

        try {
            eventosProcessados.registrarSeNovo(evento.eventId())
                    .flatMap(primeiraEntrega -> {
                        if (!primeiraEntrega) {
                            log.info("Posicao do aluno {} ignorada (eventId {}): evento ja processado",
                                    evento.alunoId(), evento.eventId());
                            return Mono.empty();
                        }
                        return orchestrator.processAluno(evento.alunoId(), evento.viagemId(), evento.latitude(),
                                evento.longitude(), evento.timestamp());
                    })
                    .block(tempoLimite);
        } catch (RuntimeException e) {
            liberarMarcador(evento.eventId());
            throw e;
        }

        ack.acknowledge();
    }

    private void liberarMarcador(String eventId) {
        try {
            eventosProcessados.liberar(eventId).block(Duration.ofSeconds(2));
        } catch (RuntimeException e) {
            log.warn("Falha ao liberar o marcador do evento {}: {}", eventId, e.getMessage());
        }
    }

    private boolean invalido(PosicaoAlunoEvento evento) {
        return evento == null
                || evento.eventId() == null || evento.eventId().isBlank()
                || evento.alunoId() == null || evento.alunoId().isBlank()
                || evento.viagemId() == null || evento.viagemId().isBlank()
                || evento.timestamp() <= 0;
    }
}
