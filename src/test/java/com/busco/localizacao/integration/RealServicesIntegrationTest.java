package com.busco.localizacao.integration;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.entity.TrackingState;
import com.busco.localizacao.domain.services.TrackingEventPublisher;
import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.redis.AlunoEmbarqueRedisRepository;
import com.busco.localizacao.infra.redis.RotaRedisRepository;
import com.busco.localizacao.infra.redis.TrackingStateRepository;
import com.busco.localizacao.infra.rabbit.RabbitTrackingEventPublisher;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de integração com serviços reais (Redis, RabbitMQ, H2).
 *
 * Este teste exercita o contexto real do Spring Boot com:
 * - Redis real (container Docker)
 * - RabbitMQ real (container Docker)
 * - Banco de dados H2 (compatível com PostgreSQL)
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
class RealServicesIntegrationTest {

    @Autowired
    private TrackingOrchestratorService orchestrator;

    @Autowired
    private TrackingStateRepository trackingStateRepository;

    @Autowired
    private RotaRedisRepository rotaRedisRepository;

    @Autowired
    private AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepository;

    @Autowired
    private TrackingEventPublisher eventPublisher;

    private static final String VIAGEM_ID = "viagem-real-test";

    @Test
    void pipelineCompleto_comRedisReal() {
        PontoRota ponto = new PontoRota("ponto-1", "Escola Test", -23.5600, -46.6400, PontoRota.TipoPonto.ESCOLA);
        rotaRedisRepository.salvarPontosRota(VIAGEM_ID, List.of(ponto)).block();

        orchestrator.processMotorista(
                VIAGEM_ID,
                -23.5505,
                -46.6333,
                System.currentTimeMillis(),
                0.85,
                5.0,
                "gps"
        ).block();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            TrackingState state = trackingStateRepository.find("viagem:" + VIAGEM_ID).block();
            assertNotNull(state, "Estado deve ser persistido no Redis");
            assertEquals(-23.5505, state.getLastLat(), 0.0001);
            assertEquals(-46.6333, state.getLastLng(), 0.0001);
        });
    }

    @Test
    void pipelineCompleto_comRabbitMQReal() {
        assertTrue(eventPublisher instanceof RabbitTrackingEventPublisher,
                "Deve usar RabbitTrackingEventPublisher em produção");

        PontoRota ponto = new PontoRota("ponto-1", "Escola Test", -23.5600, -46.6400, PontoRota.TipoPonto.ESCOLA);
        rotaRedisRepository.salvarPontosRota(VIAGEM_ID, List.of(ponto)).block();

        assertDoesNotThrow(() -> {
            orchestrator.processMotorista(
                    VIAGEM_ID,
                    -23.5505,
                    -46.6333,
                    System.currentTimeMillis(),
                    0.85,
                    5.0,
                    "gps"
            ).block();
        });
    }

    @Test
    void estadoPersistidoNoRedis_aposProcessamento() {
        PontoRota ponto = new PontoRota("ponto-1", "Escola Test", -23.5600, -46.6400, PontoRota.TipoPonto.ESCOLA);
        rotaRedisRepository.salvarPontosRota(VIAGEM_ID, List.of(ponto)).block();

        orchestrator.processMotorista(
                VIAGEM_ID,
                -23.5505,
                -46.6333,
                System.currentTimeMillis(),
                0.85,
                5.0,
                "gps"
        ).block();

        TrackingState state = trackingStateRepository.find("viagem:" + VIAGEM_ID).block();
        assertNotNull(state);
        assertEquals(-23.5505, state.getLastLat(), 0.0001);
        assertEquals(-46.6333, state.getLastLng(), 0.0001);
        assertNotNull(state.getLastTimestamp());
    }

    @Test
    void rotaRecuperadaDoRedis() {
        PontoRota ponto = new PontoRota("ponto-1", "Escola Test", -23.5600, -46.6400, PontoRota.TipoPonto.ESCOLA);
        rotaRedisRepository.salvarPontosRota(VIAGEM_ID, List.of(ponto)).block();

        List<PontoRota> pontos = rotaRedisRepository.buscarPontosRota(VIAGEM_ID).block();
        assertNotNull(pontos);
        assertEquals(1, pontos.size());
        assertEquals("ponto-1", pontos.get(0).getId());
        assertEquals("Escola Test", pontos.get(0).getNome());
    }

    @Test
    void embarquesRecuperadosDoRedis() {
        PontoEmbarque embarque = new PontoEmbarque("aluno-1", "ponto-1", -23.5600, -46.6400, PontoEmbarque.TipoEmbarque.EMBARQUE);
        alunoEmbarqueRedisRepository.salvarPontoEmbarque(VIAGEM_ID, "aluno-1", embarque).block();

        Map<String, PontoEmbarque> embarques = alunoEmbarqueRedisRepository.buscarMapaEmbarques(VIAGEM_ID).block();
        assertNotNull(embarques);
        assertEquals(1, embarques.size());
        assertTrue(embarques.containsKey("aluno-1"));
    }
}
