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
 * Entrada do stream de posicoes do veiculo.
 *
 * <p>O commit do offset e manual e acontece depois de o pipeline concluir: em caso de falha a
 * excecao sobe e o container reentrega (com retentativa limitada e dead letter). Evento fora do
 * contrato (sem viagem, sem `timestamp` valido ou sem `eventId`) e descartado com log e reconhecido,
 * porque reentregar nao o tornaria valido.</p>
 */
@Component
public class PosicaoVeiculoKafkaListener {

    private static final Logger log = LoggerFactory.getLogger(PosicaoVeiculoKafkaListener.class);

    private final TrackingOrchestratorService orchestrator;
    private final EventoProcessadoRepository eventosProcessados;
    private final Duration tempoLimite;

    public PosicaoVeiculoKafkaListener(TrackingOrchestratorService orchestrator,
                                       EventoProcessadoRepository eventosProcessados,
                                       @Value("${tracking.tempo-limite-processamento-ms:15000}") long tempoLimiteMs) {
        this.orchestrator = orchestrator;
        this.eventosProcessados = eventosProcessados;
        this.tempoLimite = Duration.ofMillis(tempoLimiteMs);
    }

    @KafkaListener(topics = "${tracking.topico-veiculo}", groupId = "${spring.kafka.consumer.group-id}",
            properties = "spring.json.value.default.type=com.busco.localizacao.infra.kafka.PosicaoVeiculoEvento")
    public void consumir(ConsumerRecord<String, PosicaoVeiculoEvento> registro, Acknowledgment ack) {
        PosicaoVeiculoEvento evento = registro.value();
        if (invalido(evento)) {
            log.warn("Posicao do motorista descartada (topico {}, offset {}): evento fora do contrato",
                    registro.topic(), registro.offset());
            ack.acknowledge();
            return;
        }

        try {
            eventosProcessados.registrarSeNovo(evento.eventId())
                    .flatMap(primeiraEntrega -> {
                        if (!primeiraEntrega) {
                            log.info("Posicao do motorista ignorada (viagem {}, eventId {}): evento ja processado",
                                    evento.viagemId(), evento.eventId());
                            return Mono.empty();
                        }
                        return orchestrator.processMotorista(evento.viagemId(), evento.latitude(), evento.longitude(),
                                evento.timestamp(), evento.bateria(), evento.precisao(), evento.provedor());
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

    private boolean invalido(PosicaoVeiculoEvento evento) {
        return evento == null
                || evento.eventId() == null || evento.eventId().isBlank()
                || evento.viagemId() == null || evento.viagemId().isBlank()
                || evento.timestamp() <= 0;
    }
}
