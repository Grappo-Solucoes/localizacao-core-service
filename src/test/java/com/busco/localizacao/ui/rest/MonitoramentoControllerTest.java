package com.busco.localizacao.ui.rest;

import com.busco.localizacao.domain.services.ChegadaVeiculoMonitor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

import static org.mockito.Mockito.*;

/**
 * Verifica as rotas REST de ciclo de vida do monitoramento de chegada contra os
 * caminhos exigidos pela spec do change
 * {@code substituicao-gradual-localizacao-core-service} (tasks 6.3/6.4).
 */
class MonitoramentoControllerTest {

    private ChegadaVeiculoMonitor chegadaVeiculoMonitor;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        chegadaVeiculoMonitor = mock(ChegadaVeiculoMonitor.class);
        client = WebTestClient.bindToController(new MonitoramentoController(chegadaVeiculoMonitor)).build();
    }

    @Test
    void postMonitoramentoIniciarEmApiViagensIdMonitoramentoIniciar() {
        client.post().uri("/api/viagens/viagem-7/monitoramento/iniciar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("monitoramento_iniciado")
                .jsonPath("$.viagemId").isEqualTo("viagem-7");

        verify(chegadaVeiculoMonitor).iniciarMonitoramento("viagem-7");
    }

    @Test
    void postMonitoramentoFinalizarEmApiViagensIdMonitoramentoFinalizar() {
        client.post().uri("/api/viagens/viagem-7/monitoramento/finalizar")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("monitoramento_finalizado")
                .jsonPath("$.viagemId").isEqualTo("viagem-7");

        verify(chegadaVeiculoMonitor).encerrarMonitoramento("viagem-7");
    }
}
