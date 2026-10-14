package com.busco.localizacao.infra.kafka;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.redis.EventoProcessadoRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.Acknowledgment;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PosicaoVeiculoKafkaListenerTest {

    private static final long TIMESTAMP_APARELHO = 1_700_000_000_000L;
    private static final String TOPICO = "tracking.posicao-veiculo.v1";

    private TrackingOrchestratorService orchestrator;
    private EventoProcessadoRepository eventosProcessados;
    private Acknowledgment ack;
    private PosicaoVeiculoKafkaListener listener;

    @BeforeEach
    void setUp() {
        orchestrator = mock(TrackingOrchestratorService.class);
        eventosProcessados = mock(EventoProcessadoRepository.class);
        ack = mock(Acknowledgment.class);
        listener = new PosicaoVeiculoKafkaListener(orchestrator, eventosProcessados, 5_000);
    }

    @Test
    void entregaAoOrquestradorComTodosOsCamposQuandoPrimeiraEntrega() {
        when(eventosProcessados.registrarSeNovo("ev-1")).thenReturn(Mono.just(true));
        when(orchestrator.processMotorista(anyString(), anyDouble(), anyDouble(), anyLong(), any(), any(), any()))
                .thenReturn(Mono.empty());

        listener.consumir(registro(evento("ev-1", 88.0, 5.5, "gps")), ack);

        verify(orchestrator).processMotorista("v1", -23.55, -46.63, TIMESTAMP_APARELHO, 88.0, 5.5, "gps");
        verify(ack).acknowledge();
    }

    @Test
    void aceitaEventoSemCamposOpcionais() {
        when(eventosProcessados.registrarSeNovo("ev-1")).thenReturn(Mono.just(true));
        when(orchestrator.processMotorista(anyString(), anyDouble(), anyDouble(), anyLong(), any(), any(), any()))
                .thenReturn(Mono.empty());

        listener.consumir(registro(evento("ev-1", null, null, null)), ack);

        verify(orchestrator).processMotorista("v1", -23.55, -46.63, TIMESTAMP_APARELHO, null, null, null);
        verify(ack).acknowledge();
    }

    @Test
    void descartaEventoForaDoContratoEReconhece() {
        listener.consumir(registro(new PosicaoVeiculoEvento("ev-1", "v1", -23.55, -46.63, 0L,
                null, null, null, null)), ack);
        listener.consumir(registro(new PosicaoVeiculoEvento("ev-2", " ", -23.55, -46.63, TIMESTAMP_APARELHO,
                null, null, null, null)), ack);

        verifyNoInteractions(orchestrator);
        verifyNoInteractions(eventosProcessados);
        verify(ack, times(2)).acknowledge();
    }

    @Test
    void ignoraEventoJaProcessado() {
        when(eventosProcessados.registrarSeNovo("ev-1")).thenReturn(Mono.just(false));

        listener.consumir(registro(evento("ev-1", null, null, null)), ack);

        verifyNoInteractions(orchestrator);
        verify(ack).acknowledge();
    }

    @Test
    void falhaDoPipelineNaoReconheceParaPermitirReentrega() {
        when(eventosProcessados.registrarSeNovo("ev-1")).thenReturn(Mono.just(true));
        when(eventosProcessados.liberar("ev-1")).thenReturn(Mono.just(true));
        when(orchestrator.processMotorista(anyString(), anyDouble(), anyDouble(), anyLong(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("rabbit fora do ar")));

        var erro = assertThrows(IllegalStateException.class,
                () -> listener.consumir(registro(evento("ev-1", null, null, null)), ack));

        assertEquals("rabbit fora do ar", erro.getMessage());
        verify(ack, never()).acknowledge();
        // O marcador precisa ser liberado: a reentrega do erro handler tem de processar de novo,
        // nao ser descartada como duplicata.
        verify(eventosProcessados).liberar("ev-1");
    }

    private PosicaoVeiculoEvento evento(String eventId, Double bateria, Double precisao, String provedor) {
        return new PosicaoVeiculoEvento(eventId, "v1", -23.55, -46.63, TIMESTAMP_APARELHO,
                bateria, precisao, provedor, "2026-10-14T21:30:00Z");
    }

    private ConsumerRecord<String, PosicaoVeiculoEvento> registro(PosicaoVeiculoEvento evento) {
        return new ConsumerRecord<>(TOPICO, 0, 0L, "v1", evento);
    }
}
