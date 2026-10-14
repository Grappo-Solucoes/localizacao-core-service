package com.busco.localizacao.e2e;

import com.busco.localizacao.domain.events.ViagemChegadaEvent;
import com.busco.localizacao.domain.services.ChegadaVeiculoMonitor;
import com.busco.localizacao.domain.services.TrackingEventPublisher;
import com.busco.localizacao.infra.jpa.PontoEmbarqueEntity;
import com.busco.localizacao.infra.jpa.PontoEmbarqueJpaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Teste de integração da detecção de chegada de ponta a ponta:
 * viagem iniciada -> posições do veículo -> ponto coberto -> evento publicado.
 *
 * <p>Usa o {@link ChegadaVeiculoMonitor} real e mocka apenas as bordas
 * (repositório de pontos de embarque e publisher de eventos).</p>
 */
class ChegadaVeiculoFluxoE2ETest {

    private static final String VIAGEM_ID = "viagem-chegada-e2e";

    // ponto de embarque de referência
    private static final double PONTO_LAT = -23.5600;
    private static final double PONTO_LNG = -46.6400;

    private PontoEmbarqueJpaRepository pontoEmbarqueJpaRepository;
    private TrackingEventPublisher eventPublisher;
    private ChegadaVeiculoMonitor monitor;

    @BeforeEach
    void setUp() {
        pontoEmbarqueJpaRepository = mock(PontoEmbarqueJpaRepository.class);
        eventPublisher = mock(TrackingEventPublisher.class);
        when(eventPublisher.publishChegada(any())).thenReturn(Mono.empty());

        monitor = new ChegadaVeiculoMonitor(pontoEmbarqueJpaRepository, eventPublisher);
        ReflectionTestUtils.setField(monitor, "raioChegadaMetros", 300.0);
        ReflectionTestUtils.setField(monitor, "ttlMinutos", 120L);
    }

    private PontoEmbarqueEntity ponto(String viagemId, String alunoId, double lat, double lng) {
        PontoEmbarqueEntity ponto = new PontoEmbarqueEntity();
        ponto.setId("ponto-" + alunoId);
        ponto.setViagemId(viagemId);
        ponto.setAlunoId(alunoId);
        ponto.setNome("Ponto " + alunoId);
        ponto.setLatitude(lat);
        ponto.setLongitude(lng);
        ponto.setTipo("EMBARQUE");
        ponto.setRealizado(false);
        return ponto;
    }

    @Test
    void viagemIniciadaPosicaoDentroDoRaio_publicaEventoDeChegada() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto(VIAGEM_ID, "aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);

        // veículo praticamente em cima do ponto
        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);

        ArgumentCaptor<ViagemChegadaEvent> captor =
                ArgumentCaptor.forClass(ViagemChegadaEvent.class);
        verify(eventPublisher).publishChegada(captor.capture());

        ViagemChegadaEvent evento = captor.getValue();
        assertEquals(VIAGEM_ID, evento.getViagemId());
        assertEquals(PONTO_LAT, evento.getLatitude(), 1e-9);
        assertEquals(PONTO_LNG, evento.getLongitude(), 1e-9);
        assertEquals(java.util.Set.of("aluno-1"), evento.getAlunos());
    }

    @Test
    void posicaoForaDoRaio_naoPublicaEvento() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto(VIAGEM_ID, "aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);

        // ~4,5 km de distancia (muito acima do raio de 300 m)
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.6000, -46.7000);

        verify(eventPublisher, never()).publishChegada(any());
    }

    @Test
    void alunosNaMesmaCoordenada_publicamUmUnicoEventoComTodos() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(
                        ponto(VIAGEM_ID, "aluno-1", PONTO_LAT, PONTO_LNG),
                        ponto(VIAGEM_ID, "aluno-2", PONTO_LAT, PONTO_LNG)
                ));

        monitor.iniciarMonitoramento(VIAGEM_ID);

        // duas posições dentro do raio: o ponto unificado só é notificado uma vez
        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);

        ArgumentCaptor<ViagemChegadaEvent> captor =
                ArgumentCaptor.forClass(ViagemChegadaEvent.class);
        verify(eventPublisher, times(1)).publishChegada(captor.capture());
        assertEquals(java.util.Set.of("aluno-1", "aluno-2"), captor.getValue().getAlunos());
    }

    @Test
    void todosPontosCobertos_encerraMonitoramentoENaoNotificaNovamente() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto(VIAGEM_ID, "aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);

        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);
        // segunda posição após cobrir o único ponto: monitor já encerrado
        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);

        verify(eventPublisher, times(1)).publishChegada(any());
    }

    @Test
    void viagemFinalizada_encerraMonitoramentoENaoNotifica() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto(VIAGEM_ID, "aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        monitor.encerrarMonitoramento(VIAGEM_ID);

        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);

        verify(eventPublisher, never()).publishChegada(any());
    }

    @Test
    void viagemSemPontosDeEmbarque_naoAbreMonitoramento() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of());

        monitor.iniciarMonitoramento(VIAGEM_ID);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);

        verify(eventPublisher, never()).publishChegada(any());
    }
}
