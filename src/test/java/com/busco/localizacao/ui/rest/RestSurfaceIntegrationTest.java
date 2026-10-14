package com.busco.localizacao.ui.rest;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.reactive.AutoConfigureWebTestClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifica a superfície HTTP real da aplicação (servidor embarcado), cobrindo dois
 * cenários do spec do change {@code substituicao-gradual-localizacao-core-service}
 * (requisito "Superfície REST de produção"): documentação em OpenAPI e tratamento de
 * erro centralizado.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureWebTestClient
@ActiveProfiles("test")
class RestSurfaceIntegrationTest {

    @Autowired
    private WebTestClient webTestClient;

    @MockBean
    private TrackingOrchestratorService orchestratorService;

    @Test
    void apiDocumentadaEmOpenApiComOsEndpointsDeProducao() {
        webTestClient.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .value(body -> {
                    assertTrue(body.contains("/api/viagens"),
                            "OpenAPI deve listar os endpoints de posicao; corpo: " + body);
                    assertTrue(body.contains("posicoes"),
                            "OpenAPI deve descrever a operacao de ingestao de posicao");
                });
    }

    @Test
    void rotaInexistenteContinuaSendo404() {
        // um @ExceptionHandler(Exception.class) guloso pode capturar o 404 do proprio
        // framework e devolver 500; este teste protege esse comportamento
        webTestClient.get().uri("/api/nao-existe")
                .exchange()
                .expectStatus().isNotFound();
    }

    @Test
    void corpoInvalidoRetorna400PeloHandlerCentral() {
        // falta latitude: a falha de validacao do framework deve virar 400, nao 500
        webTestClient.post().uri("/api/viagens/viagem-1/posicoes")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("longitude", -46.6333, "timestamp", 1_700_000_000_000L))
                .exchange()
                .expectStatus().isBadRequest();
    }
}
