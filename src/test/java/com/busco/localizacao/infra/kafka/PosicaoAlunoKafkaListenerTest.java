package com.busco.localizacao.infra.kafka;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.redis.EventoProcessadoRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PosicaoAlunoKafkaListenerTest {

    private static final long TIMESTAMP_APARELHO = 1_700_000_000_000L;
    private static final String TOPICO = "tracking.posicao-aluno.v1";

    private TrackingOrchestratorService orchestrator;
    private EventoProcessadoRepository eventosProcessados;
    private Acknowledgment ack;
    private PosicaoAlunoKafkaListener listener;

    @BeforeEach
    void setUp() {
        orchestrator = mock(TrackingOrchestratorService.class);
        eventosProcessados = mock(EventoProcessadoRepository.class);
        ack = mock(Acknowledgment.class);
        listener = new PosicaoAlunoKafkaListener(orchestrator, eventosProcessados, 5_000);
    }

    @Test
    void entregaAoOrquestradorComAlunoEViagem() {
        when(eventosProcessados.registrarSeNovo("ev-1")).thenReturn(Mono.just(true));
        when(orchestrator.processAluno(anyString(), anyString(), anyDouble(), anyDouble(), anyLong()))
                .thenReturn(Mono.empty());

        listener.consumir(registro(evento("ev-1", "a1", "v1")), ack);

        verify(orchestrator).processAluno("a1", "v1", -23.55, -46.63, TIMESTAMP_APARELHO);
        verify(ack).acknowledge();
    }

    @Test
    void descartaEventoSemTimestampValido() {
        listener.consumir(registro(new PosicaoAlunoEvento("ev-1", "v1", "a1", -23.55, -46.63, 0L,
                null)), ack);

        verifyNoInteractions(orchestrator);
        verify(ack).acknowledge();
    }

    @Test
    void descartaEventoSemAluno() {
        listener.consumir(registro(new PosicaoAlunoEvento("ev-1", "v1", " ", -23.55, -46.63,
                TIMESTAMP_APARELHO, null)), ack);

        verifyNoInteractions(orchestrator);
        verify(ack).acknowledge();
    }

    @Test
    void ignoraEventoJaProcessado() {
        when(eventosProcessados.registrarSeNovo("ev-1")).thenReturn(Mono.just(false));

        listener.consumir(registro(evento("ev-1", "a1", "v1")), ack);

        verifyNoInteractions(orchestrator);
        verify(ack).acknowledge();
    }

    @Test
    void falhaDoPipelineNaoReconheceELiberaOMarcador() {
        when(eventosProcessados.registrarSeNovo("ev-1")).thenReturn(Mono.just(true));
        when(eventosProcessados.liberar("ev-1")).thenReturn(Mono.just(true));
        when(orchestrator.processAluno(anyString(), anyString(), anyDouble(), anyDouble(), anyLong()))
                .thenReturn(Mono.error(new IllegalStateException("rabbit fora do ar")));

        assertThrows(IllegalStateException.class,
                () -> listener.consumir(registro(evento("ev-1", "a1", "v1")), ack));

        verify(ack, never()).acknowledge();
        verify(eventosProcessados).liberar("ev-1");
    }

    private PosicaoAlunoEvento evento(String eventId, String alunoId, String viagemId) {
        return new PosicaoAlunoEvento(eventId, viagemId, alunoId, -23.55, -46.63, TIMESTAMP_APARELHO,
                "2026-10-14T21:30:00Z");
    }

    private ConsumerRecord<String, PosicaoAlunoEvento> registro(PosicaoAlunoEvento evento) {
        return new ConsumerRecord<>(TOPICO, 0, 0L, evento.alunoId(), evento);
    }
}
