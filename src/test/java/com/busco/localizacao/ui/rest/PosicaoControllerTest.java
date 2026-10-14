package com.busco.localizacao.ui.rest;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.rest.advice.ExceptionAdvice;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Verifica as rotas REST de ingestão de posição contra os caminhos exigidos pela
 * spec do change {@code substituicao-gradual-localizacao-core-service} (tasks 6.1/6.2).
 */
class PosicaoControllerTest {

    private TrackingOrchestratorService orchestratorService;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        orchestratorService = mock(TrackingOrchestratorService.class);
        when(orchestratorService.processMotorista(
                anyString(), anyDouble(), anyDouble(), anyLong(), any(), any(), any()))
                .thenReturn(Mono.empty());
        when(orchestratorService.processAluno(
                anyString(), anyString(), anyDouble(), anyDouble(), anyLong()))
                .thenReturn(Mono.empty());

        client = WebTestClient.bindToController(new PosicaoController(orchestratorService))
                .controllerAdvice(new ExceptionAdvice())
                .build();
    }

    @Test
    void postPosicoesDoMotoristaEmApiViagensIdPosicoes() {
        client.post().uri("/api/viagens/viagem-1/posicoes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "latitude", -23.5505,
                        "longitude", -46.6333,
                        "timestamp", 1_700_000_000_000L,
                        "batteryLevel", 82.0,
                        "accuracy", 5.0,
                        "provider", "gps"))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("processado")
                .jsonPath("$.viagemId").isEqualTo("viagem-1");

        verify(orchestratorService).processMotorista(
                eq("viagem-1"), eq(-23.5505), eq(-46.6333), eq(1_700_000_000_000L),
                eq(82.0), eq(5.0), eq("gps"));
    }

    @Test
    void postPosicoesDoAlunoEmApiViagensIdAlunosAlunoIdPosicoes() {
        client.post().uri("/api/viagens/viagem-2/alunos/aluno-9/posicoes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "latitude", -23.56,
                        "longitude", -46.64,
                        "timestamp", 1_700_000_000_500L))
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("processado")
                .jsonPath("$.alunoId").isEqualTo("aluno-9");

        verify(orchestratorService).processAluno(
                eq("aluno-9"), eq("viagem-2"), eq(-23.56), eq(-46.64), eq(1_700_000_000_500L));
    }

    @Test
    void erroDeDominioETraduzidoPeloHandlerCentral() {
        // spec replacement-compatibilidade: erro do dominio vira status HTTP adequado
        // por handler central, sem excecao vazando para o cliente
        when(orchestratorService.processMotorista(
                anyString(), anyDouble(), anyDouble(), anyLong(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalArgumentException("viagem invalida")));

        client.post().uri("/api/viagens/viagem-1/posicoes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "latitude", -23.5505,
                        "longitude", -46.6333,
                        "timestamp", 1_700_000_000_000L))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.error").isEqualTo("viagem invalida");
    }

    @Test
    void prefixoApiV1NaoEExposto() {
        // a spec e os servicos irmaos usam /api/<recurso>, sem versao no caminho
        client.post().uri("/api/v1/viagens/viagem-1/posicoes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "latitude", -23.5,
                        "longitude", -46.6,
                        "timestamp", 1_700_000_000_000L))
                .exchange()
                .expectStatus().isNotFound();

        verify(orchestratorService, never()).processMotorista(
                anyString(), anyDouble(), anyDouble(), anyLong(), any(), any(), any());
    }
}
