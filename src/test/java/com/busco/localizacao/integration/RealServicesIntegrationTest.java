package com.busco.localizacao.integration;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.entity.TrackingState;
import com.busco.localizacao.domain.services.GeocodingService;
import com.busco.localizacao.domain.services.TrackingEventPublisher;
import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.redis.AlunoEmbarqueRedisRepository;
import com.busco.localizacao.infra.redis.RotaRedisRepository;
import com.busco.localizacao.infra.redis.TrackingStateRepository;
import com.busco.localizacao.infra.rabbit.RabbitTrackingEventPublisher;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate de aceitação com serviços reais (Redis e RabbitMQ em containers Docker, H2):
 * exercita o contexto real do Spring Boot, os serializadores reais de Redis e o broker
 * real, incluindo o contrato da mensagem observado no fio (exchange, routing key,
 * headers e corpo JSON).
 *
 * <p>É o único teste que pega defeitos de serialização/integração que os testes com mock
 * escondem — foi assim que apareceram as falhas de desserialização de
 * `TrackingState`/`PontoRota` e o hash de embarque sem `hashValue`.
 *
 * <p>Fica fora do build default (`@Tag("integration")` + `-DexcludedGroups=integration`)
 * porque exige os containers em `localhost`. Rodar com: `./mvnw test -DexcludedGroups=`.
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

    @Autowired
    private GeocodingService geocodingService;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private StringRedisTemplate chavesRedis;

    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String VIAGEM_ID = "viagem-real-test";

    private static final String VIAGEM_HASH_ID = "viagem-real-test-hash";

    private static final String FILA_VERIFICACAO = "verificacao.localizacao.posicao";

    /**
     * Limpa o estado que estes testes escrevem no Redis real. E necessario porque agora
     * as escritas funcionam de verdade: sem isso os hashes/lists acumulam entre
     * execucoes e as contagens passam a depender da ordem dos testes.
     */
    @BeforeEach
    void limparEstadoNoRedis() {
        chavesRedis.delete(List.of(
                "rota:" + VIAGEM_ID + ":pontos",
                "rota:" + VIAGEM_HASH_ID + ":pontos",
                "embarque:" + VIAGEM_ID,
                "embarque:" + VIAGEM_HASH_ID,
                "viagem:" + VIAGEM_ID,
                "viagem:" + VIAGEM_HASH_ID
        ));
    }

    @AfterEach
    void removerFilaDeVerificacao() {
        if (amqpAdmin != null) {
            amqpAdmin.deleteQueue(FILA_VERIFICACAO);
        }
    }

    /**
     * Vincula uma fila (auto-delete, removida no @AfterEach) à exchange dedicada, para
     * observar exatamente o que o serviço publica.
     */
    private void escutarExchangeDedicada(String routingKey) {
        Queue fila = new Queue(FILA_VERIFICACAO, false, false, true);
        amqpAdmin.declareQueue(fila);
        Binding binding = BindingBuilder.bind(fila)
                .to(new TopicExchange("location.exchange"))
                .with(routingKey);
        amqpAdmin.declareBinding(binding);
    }

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

    // -----------------------------------------------------------------
    // Round trips reais: o que o serviço grava no Redis precisa voltar inteiro.
    // Sem os mixins de RedisReactiveConfig, todos estes falham com
    // "Cannot construct instance of ... (no Creators, like default constructor, exist)".
    // -----------------------------------------------------------------

    @Test
    void trackingStateFazRoundTripNoRedisReal() {
        TrackingState estado = new TrackingState(-23.5505, -46.6333, 1_700_000_000_000L, 42.5);
        trackingStateRepository.save("viagem:" + VIAGEM_ID, estado).block();

        TrackingState lido = trackingStateRepository.find("viagem:" + VIAGEM_ID).block();

        assertNotNull(lido, "estado gravado no Redis precisa ser lido de volta");
        assertEquals(-23.5505, lido.getLastLat(), 1e-9);
        assertEquals(-46.6333, lido.getLastLng(), 1e-9);
        assertEquals(1_700_000_000_000L, lido.getLastTimestamp());
        assertEquals(42.5, lido.getLastSpeed(), 1e-9);
    }

    @Test
    void pontosRotaPreservamEstadoNoRedisReal() {
        PontoRota ponto = new PontoRota("ponto-visitado", "Escola Test", -23.5600, -46.6400,
                PontoRota.TipoPonto.ESCOLA);
        ponto.setVisitado(true);
        ponto.setHorarioPrevisto(1_700_000_000_000L);
        rotaRedisRepository.salvarPontosRota(VIAGEM_ID, List.of(ponto)).block();

        List<PontoRota> lidos = rotaRedisRepository.buscarPontosRota(VIAGEM_ID).block();

        assertNotNull(lidos, "lista de pontos precisa ser lida de volta");
        assertEquals(1, lidos.size());
        PontoRota lido = lidos.get(0);
        assertEquals("ponto-visitado", lido.getId());
        assertEquals("Escola Test", lido.getNome());
        assertEquals(PontoRota.TipoPonto.ESCOLA, lido.getTipo());
        assertEquals(-23.5600, lido.getLatitude(), 1e-9);
        assertEquals(-46.6400, lido.getLongitude(), 1e-9);
        assertTrue(lido.isVisitado(), "visitado precisa sobreviver ao round trip");
        assertEquals(1_700_000_000_000L, lido.getHorarioPrevisto());
    }

    @Test
    void embarqueFazRoundTripEmHashNoRedisReal() {
        PontoEmbarque embarque = new PontoEmbarque("aluno-hash", "ponto-1", -23.5600, -46.6400,
                PontoEmbarque.TipoEmbarque.EMBARQUE);
        alunoEmbarqueRedisRepository.salvarPontoEmbarque(VIAGEM_HASH_ID, "aluno-hash", embarque).block();

        PontoEmbarque lido = alunoEmbarqueRedisRepository.buscarPontoEmbarque(VIAGEM_HASH_ID, "aluno-hash").block();
        assertNotNull(lido, "embarque gravado em hash precisa ser lido de volta");
        assertEquals("aluno-hash", lido.getAlunoId());
        assertEquals("ponto-1", lido.getPontoId());
        assertEquals(PontoEmbarque.StatusEmbarque.PENDENTE, lido.getStatus());
        assertFalse(lido.isRealizado());

        // marcarRealizado le, altera e grava de volta pelo mesmo caminho
        alunoEmbarqueRedisRepository.marcarRealizado(VIAGEM_HASH_ID, "aluno-hash").block();
        PontoEmbarque depois = alunoEmbarqueRedisRepository.buscarPontoEmbarque(VIAGEM_HASH_ID, "aluno-hash").block();
        assertNotNull(depois);
        assertTrue(depois.isRealizado(), "marcarRealizado precisa persistir no hash");
    }

    @Test
    void eventoDePosicaoChegaNaExchangeDedicadaComHeadersECorpoEsperados() throws Exception {
        escutarExchangeDedicada("viagem.posicao");

        PontoRota ponto = new PontoRota("ponto-1", "Escola Test", -23.5600, -46.6400,
                PontoRota.TipoPonto.ESCOLA);
        rotaRedisRepository.salvarPontosRota(VIAGEM_ID, List.of(ponto)).block();

        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, System.currentTimeMillis(), 0.85, 5.0, "gps"
        ).block();

        Message mensagem = rabbit.receive(FILA_VERIFICACAO, 10_000);
        assertNotNull(mensagem, "nenhuma mensagem chegou na fila vinculada a location.exchange");

        // Contrato de roteamento (spec localizacao/eventos)
        assertEquals("location.exchange", mensagem.getMessageProperties().getReceivedExchange());
        assertEquals("viagem.posicao", mensagem.getMessageProperties().getReceivedRoutingKey());
        assertEquals("ViagemPosicaoAtualizadaEvent", mensagem.getMessageProperties().getHeader("event-type"));
        assertEquals("1.0", mensagem.getMessageProperties().getHeader("version"));
        assertNotNull(mensagem.getMessageProperties().getHeader("timestamp"));

        // Corpo JSON como um consumidor externo veria
        JsonNode corpo = JSON.readTree(mensagem.getBody());
        assertEquals(VIAGEM_ID, corpo.get("viagemId").asText());
        assertTrue(corpo.hasNonNull("latitude"));
        assertTrue(corpo.hasNonNull("longitude"));
        assertTrue(corpo.hasNonNull("status"));

        JsonNode endereco = corpo.get("endereco");
        if (endereco != null && !endereco.isNull()) {
            String texto = endereco.asText();
            assertFalse(texto.contains("não disponível") || texto.contains("nao disponivel"),
                    "endereco nunca pode ser o texto sentinela: " + texto);
            assertFalse(texto.isBlank(), "endereco nao pode ser string vazia");
        }
    }

    @Test
    void eventoDePosicaoChegaAoBrokerMesmoSemRotaNoRedis() throws Exception {
        // Cenario reproduzido no jar empacotado: viagem sem chave rota:{id}:pontos no
        // Redis. Antes, o Mono.zip (GET vazio) zerava o pipeline: REST respondia 200 e
        // NENHUM evento chegava ao broker. O @BeforeEach garante a chave ausente.
        escutarExchangeDedicada("viagem.posicao");

        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, System.currentTimeMillis(), null, null, "gps"
        ).block();

        Message mensagem = rabbit.receive(FILA_VERIFICACAO, 10_000);
        assertNotNull(mensagem, "posicao de viagem sem rota precisa ser publicada");
        assertEquals("viagem.posicao", mensagem.getMessageProperties().getReceivedRoutingKey());

        JsonNode corpo = JSON.readTree(mensagem.getBody());
        assertEquals(VIAGEM_ID, corpo.get("viagemId").asText());
        assertFalse(corpo.hasNonNull("proximoPontoId"), "sem rota, nao ha proximo ponto");
    }

    @Test
    void geocodificacaoRealNuncaInventaEndereco() {
        // Chama o provedor real. Se a rede estiver bloqueada, o serviço devolve vazio
        // (nunca texto sentinela) e o teste continua valido: o contrato verificado é
        // "endereço real ou ausência de endereço".
        String endereco = geocodingService.reverseGeocode(-23.5505, -46.6333).block();

        if (endereco != null) {
            assertFalse(endereco.isBlank());
            assertFalse(endereco.contains("não disponível"), "sentinela nao pode voltar como endereco");
        }
        System.out.println("[verificacao] geocodificacao real (-23.5505,-46.6333) -> " + endereco);
    }
}
