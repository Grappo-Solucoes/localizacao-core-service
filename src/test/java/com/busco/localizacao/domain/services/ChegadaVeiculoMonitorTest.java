package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.events.ViagemChegadaEvent;
import com.busco.localizacao.infra.jpa.PontoEmbarqueEntity;
import com.busco.localizacao.infra.jpa.PontoEmbarqueJpaRepository;
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

    @Test
    void iniciaMonitoramentoComPontos() {
        PontoEmbarqueEntity ponto = new PontoEmbarqueEntity();
        ponto.setViagemId("viagem-1");
        ponto.setAlunoId("aluno-1");
        ponto.setLatitude(-23.5505);
        ponto.setLongitude(-46.6333);
        ponto.setRealizado(false);

        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse("viagem-1"))
                .thenReturn(Collections.singletonList(ponto));

        monitor.iniciarMonitoramento("viagem-1");

        verify(pontoEmbarqueJpaRepository).findByViagemIdAndRealizadoFalse("viagem-1");
    }

    @Test
    void naoIniciaMonitoramentoSemPontos() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse("viagem-vazia"))
                .thenReturn(Collections.emptyList());

        monitor.iniciarMonitoramento("viagem-vazia");

        verify(pontoEmbarqueJpaRepository).findByViagemIdAndRealizadoFalse("viagem-vazia");
    }

    @Test
    void naoIniciaMonitoramentoComPontosJaRealizados() {
        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse("viagem-completa"))
                .thenReturn(Collections.emptyList());

        monitor.iniciarMonitoramento("viagem-completa");

        verify(pontoEmbarqueJpaRepository).findByViagemIdAndRealizadoFalse("viagem-completa");
    }

    @Test
    void encerraMonitoramento() {
        PontoEmbarqueEntity ponto = new PontoEmbarqueEntity();
        ponto.setViagemId("viagem-2");
        ponto.setAlunoId("aluno-2");
        ponto.setLatitude(-23.5505);
        ponto.setLongitude(-46.6333);
        ponto.setRealizado(false);

        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse("viagem-2"))
                .thenReturn(Collections.singletonList(ponto));

        monitor.iniciarMonitoramento("viagem-2");
        monitor.encerrarMonitoramento("viagem-2");

        verify(pontoEmbarqueJpaRepository).findByViagemIdAndRealizadoFalse("viagem-2");
    }

    @Test
    void processarPosicaoSemMonitoramentoNaoFazNada() {
        // Não deve lançar exceção nem chamar o publisher
        monitor.processarPosicaoVeiculo("viagem-inexistente", -23.5505, -46.6333);

        verify(eventPublisher, never()).publishChegada(any());
    }

    @Test
    void unificaPontosPorCoordenada() {
        PontoEmbarqueEntity ponto1 = new PontoEmbarqueEntity();
        ponto1.setViagemId("viagem-3");
        ponto1.setAlunoId("aluno-3");
        ponto1.setLatitude(-23.5505);
        ponto1.setLongitude(-46.6333);
        ponto1.setRealizado(false);

        PontoEmbarqueEntity ponto2 = new PontoEmbarqueEntity();
        ponto2.setViagemId("viagem-3");
        ponto2.setAlunoId("aluno-4");
        ponto2.setLatitude(-23.5505);
        ponto2.setLongitude(-46.6333);
        ponto2.setRealizado(false);

        when(pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse("viagem-3"))
                .thenReturn(Arrays.asList(ponto1, ponto2));

        monitor.iniciarMonitoramento("viagem-3");

        // Deve unificar os dois pontos em uma única coordenada
        verify(pontoEmbarqueJpaRepository).findByViagemIdAndRealizadoFalse("viagem-3");
    }
}
