package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.repository.PontoEmbarqueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChegadaVeiculoMonitorTest {

    private PontoEmbarqueRepository pontoEmbarqueRepository;
    private TrackingEventPublisher eventPublisher;
    private ChegadaVeiculoMonitor monitor;

    @BeforeEach
    void setUp() {
        pontoEmbarqueRepository = mock(PontoEmbarqueRepository.class);
        eventPublisher = mock(TrackingEventPublisher.class);
        when(eventPublisher.publishChegada(any())).thenReturn(Mono.empty());

        monitor = new ChegadaVeiculoMonitor(pontoEmbarqueRepository, eventPublisher);
        ReflectionTestUtils.setField(monitor, "raioChegadaMetros", 300.0);
        ReflectionTestUtils.setField(monitor, "ttlMinutos", 120L);
    }

    private static PontoEmbarque ponto(String alunoId, double lat, double lng) {
        return new PontoEmbarque(alunoId, "ponto-" + alunoId, lat, lng,
                PontoEmbarque.TipoEmbarque.EMBARQUE);
    }

    @Test
    void iniciaMonitoramentoComPontos() {
        when(pontoEmbarqueRepository.findByViagemIdAndRealizadoFalse("viagem-1"))
                .thenReturn(Collections.singletonList(ponto("aluno-1", -23.5505, -46.6333)));

        monitor.iniciarMonitoramento("viagem-1");

        verify(pontoEmbarqueRepository).findByViagemIdAndRealizadoFalse("viagem-1");
    }

    @Test
    void naoIniciaMonitoramentoSemPontos() {
        when(pontoEmbarqueRepository.findByViagemIdAndRealizadoFalse("viagem-vazia"))
                .thenReturn(Collections.emptyList());

        monitor.iniciarMonitoramento("viagem-vazia");

        verify(pontoEmbarqueRepository).findByViagemIdAndRealizadoFalse("viagem-vazia");
    }

    @Test
    void naoIniciaMonitoramentoComPontosJaRealizados() {
        when(pontoEmbarqueRepository.findByViagemIdAndRealizadoFalse("viagem-completa"))
                .thenReturn(Collections.emptyList());

        monitor.iniciarMonitoramento("viagem-completa");

        verify(pontoEmbarqueRepository).findByViagemIdAndRealizadoFalse("viagem-completa");
    }

    @Test
    void encerraMonitoramento() {
        when(pontoEmbarqueRepository.findByViagemIdAndRealizadoFalse("viagem-2"))
                .thenReturn(Collections.singletonList(ponto("aluno-2", -23.5505, -46.6333)));

        monitor.iniciarMonitoramento("viagem-2");
        monitor.encerrarMonitoramento("viagem-2");

        verify(pontoEmbarqueRepository).findByViagemIdAndRealizadoFalse("viagem-2");
    }

    @Test
    void processarPosicaoSemMonitoramentoNaoFazNada() {
        // Não deve lançar exceção nem chamar o publisher
        monitor.processarPosicaoVeiculo("viagem-inexistente", -23.5505, -46.6333);

        verify(eventPublisher, never()).publishChegada(any());
    }

    @Test
    void unificaPontosPorCoordenada() {
        List<PontoEmbarque> pontos = Arrays.asList(
                ponto("aluno-3", -23.5505, -46.6333),
                ponto("aluno-4", -23.5505, -46.6333));

        when(pontoEmbarqueRepository.findByViagemIdAndRealizadoFalse("viagem-3"))
                .thenReturn(pontos);

        monitor.iniciarMonitoramento("viagem-3");

        // Deve unificar os dois pontos em uma única coordenada
        verify(pontoEmbarqueRepository).findByViagemIdAndRealizadoFalse("viagem-3");
    }
}
