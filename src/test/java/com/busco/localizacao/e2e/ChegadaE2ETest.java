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
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Teste end-to-end da detecção de chegada:
 * viagem iniciada -> posições do veículo -> ponto coberto -> evento publicado.
 */
class ChegadaE2ETest {

    private static final String VIAGEM_ID = "viagem-chegada";
    private static final double PONTO_LAT = -23.5505;
    private static final double PONTO_LNG = -46.6333;

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

    private PontoEmbarqueEntity ponto(String alunoId, double lat, double lng) {
        PontoEmbarqueEntity ponto = new PontoEmbarqueEntity();
        ponto.setViagemId(VIAGEM_ID);
        ponto.setAlunoId(alunoId);
        ponto.setNome("Ponto " + alunoId);
        ponto.setLatitude(lat);
        ponto.setLongitude(lng);
        ponto.setTipo("EMBARQUE");
        ponto.setRealizado(false);
        return ponto;
    }

    @Test
    void viagemIniciada_abreMonitoramentoComPontosNaoRealizados() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);

        verify(pontoEmbarqueJpaRepository).findByViagemIdAndRealizadoFalse(VIAGEM_ID);
    }

    @Test
    void posicaoDentroDoRaio_publicaEventoDeChegada() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        // veículo a ~50m do ponto (dentro do raio de 300m)
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333);

        ArgumentCaptor<ViagemChegadaEvent> captor = ArgumentCaptor.forClass(ViagemChegadaEvent.class);
        verify(eventPublisher).publishChegada(captor.capture());

        ViagemChegadaEvent evento = captor.getValue();
        assertEquals(VIAGEM_ID, evento.getViagemId());
        assertEquals(PONTO_LAT, evento.getLatitude(), 1e-9);
        assertEquals(PONTO_LNG, evento.getLongitude(), 1e-9);
        assertTrue(evento.getAlunos().contains("aluno-1"));
        assertNotNull(evento.getOccurredOn());
    }

    @Test
    void posicaoForaDoRaio_naoPublicaEvento() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        // veículo a ~1km do ponto (fora do raio de 300m)
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.5600, -46.6333);

        verify(eventPublisher, never()).publishChegada(any());
    }

    @Test
    void pontoCobertoNaoNotificaDuasVezes() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333);

        // dedupe: ponto é removido após notificação
        verify(eventPublisher, times(1)).publishChegada(any());
    }

    @Test
    void todosPontosCobertos_encerraMonitoramento() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333);

        // após cobrir o único ponto, novas posições não geram evento
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333);
        verify(eventPublisher, times(1)).publishChegada(any());
    }

    @Test
    void multiplosAlunosNoMesmoPonto_umEventoComTodosAlunos() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(
                        ponto("aluno-1", PONTO_LAT, PONTO_LNG),
                        ponto("aluno-2", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333);

        ArgumentCaptor<ViagemChegadaEvent> captor = ArgumentCaptor.forClass(ViagemChegadaEvent.class);
        verify(eventPublisher, times(1)).publishChegada(captor.capture());

        Set<String> alunos = captor.getValue().getAlunos();
        assertEquals(2, alunos.size());
        assertTrue(alunos.containsAll(Set.of("aluno-1", "aluno-2")));
    }

    @Test
    void viagemFinalizada_encerraMonitoramentoSemNovosEventos() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        monitor.encerrarMonitoramento(VIAGEM_ID);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333);

        verify(eventPublisher, never()).publishChegada(any());
    }

    @Test
    void semPontosNaoRealizados_naoAbreMonitoramento() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of());

        monitor.iniciarMonitoramento(VIAGEM_ID);
        monitor.processarPosicaoVeiculo(VIAGEM_ID, PONTO_LAT, PONTO_LNG);

        verify(eventPublisher, never()).publishChegada(any());
    }

    @Test
    void raioDeChegadaEhDe300Metros() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);

        // ~250m: dentro do raio
        monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55275, -46.6333);
        verify(eventPublisher, times(1)).publishChegada(any());
    }

    @Test
    void chegadaPublicadaNoRabbit_naoBloqueiaEmCasoDeErro() {
        when(eventPublisher.publishChegada(any()))
                .thenReturn(Mono.error(new RuntimeException("Rabbit indisponivel")));
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(VIAGEM_ID))
                .thenReturn(List.of(ponto("aluno-1", PONTO_LAT, PONTO_LNG)));

        monitor.iniciarMonitoramento(VIAGEM_ID);
        assertDoesNotThrow(() ->
                monitor.processarPosicaoVeiculo(VIAGEM_ID, -23.55095, -46.6333));

        verify(eventPublisher).publishChegada(any());
    }
}
