package com.busco.localizacao.integration;

import com.busco.localizacao.infra.kafka.PosicaoAlunoEvento;
import com.busco.localizacao.infra.kafka.PosicaoVeiculoEvento;
import com.busco.localizacao.infra.redis.TrackingStateRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.admin.NewTopic;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Gate de aceitacao da entrada pelo Kafka: publica nos dois topicos com broker real e observa o que
 * sai no Rabbit real, depois de atravessar o pipeline (Redis real; banco H2 do perfil de teste).
 *
 * <p>Cobre o que so o caminho real mostra: a ordem por chave, o mapeamento dos campos do contrato
 * (`precisao` -> `accuracy`, `bateria`, `provedor`, `timestamp` do aparelho) e a idempotencia por
 * `eventId` — inclusive o caso em que o evento repetido traz timestamp maior, que passaria pela
 * consistencia e so o marcador de idempotencia barra.</p>
 *
 * <p>Grupo {@code integration}: exige Kafka (docker-compose.dev.yml), Redis e RabbitMQ locais.
 * Rodar com {@code ./mvnw test -DexcludedGroups=}.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
class KafkaPipelineIntegrationTest {

    /** Topicos proprios desta execucao: o teste nao depende do que ficou de execucoes anteriores. */
    private static final String TOPICO_VEICULO = "tracking.posicao-veiculo.it-" + UUID.randomUUID();
    private static final String TOPICO_ALUNO = "tracking.posicao-aluno.it-" + UUID.randomUUID();

    @DynamicPropertySource
    static void entradaIsolada(DynamicPropertyRegistry registry) {
        registry.add("tracking.topico-veiculo", () -> TOPICO_VEICULO);
        registry.add("tracking.topico-aluno", () -> TOPICO_ALUNO);
        // Grupo novo a cada execucao: sem offsets herdados, so o que este teste publica e consumido.
        registry.add("spring.kafka.consumer.group-id", () -> "localizacao-core-it-" + UUID.randomUUID());
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String VIAGEM_ID = "viagem-kafka-it";
    private static final String ALUNO_ID = "aluno-kafka-it";
    private static final String FILA = "verificacao.kafka.posicao";

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private RabbitTemplate rabbit;

    @Autowired
    private AmqpAdmin amqpAdmin;

    @Autowired
    private TrackingStateRepository trackingStateRepository;

    @Autowired
    private StringRedisTemplate chavesRedis;

    @Value("${tracking.topico-veiculo}")
    private String topicoVeiculo;

    @Value("${tracking.topico-aluno}")
    private String topicoAluno;

    @BeforeEach
    void prepararFilaEEstado() {
        chavesRedis.delete(List.of(
                "viagem:" + VIAGEM_ID,
                "aluno:" + ALUNO_ID,
                "rota:" + VIAGEM_ID + ":pontos",
                "embarque:" + VIAGEM_ID));

        // autoDelete=false de proposito: cada receive(timeout) cria e cancela um consumer, e uma
        // fila auto-delete seria apagada depois do primeiro, derrubando as leituras seguintes.
        // A limpeza fica no @AfterEach.
        Queue fila = new Queue(FILA, false, false, false);
        amqpAdmin.declareQueue(fila);
        TopicExchange exchange = new TopicExchange("location.exchange");
        amqpAdmin.declareBinding(BindingBuilder.bind(fila).to(exchange).with("viagem.posicao"));
        amqpAdmin.declareBinding(BindingBuilder.bind(fila).to(exchange).with("aluno.posicao"));
    }

    @AfterEach
    void limparFila() {
        if (amqpAdmin != null) {
            amqpAdmin.deleteQueue(FILA);
        }
    }

    @Test
    void posicoesDoKafkaChegamAoRabbitNaOrdemDaChaveEComOsCamposDoContrato() throws Exception {
        long base = System.currentTimeMillis();
        var primeira = new PosicaoVeiculoEvento(UUID.randomUUID().toString(), VIAGEM_ID,
                -23.5505, -46.6333, base, 88.0, 5.5, "gps", Instant.now().toString());
        var segunda = new PosicaoVeiculoEvento(UUID.randomUUID().toString(), VIAGEM_ID,
                -23.5510, -46.6340, base + 5_000, 87.0, 4.5, "gps", Instant.now().toString());

        kafkaTemplate.send(topicoVeiculo, VIAGEM_ID, primeira).get(10, TimeUnit.SECONDS);
        kafkaTemplate.send(topicoVeiculo, VIAGEM_ID, segunda).get(10, TimeUnit.SECONDS);

        Message primeiro = esperarEventoDaViagem("viagem.posicao", base);
        Message segundo = esperarEventoDaViagem("viagem.posicao", base + 5_000);

        assertEquals("location.exchange", primeiro.getMessageProperties().getReceivedExchange());
        assertEquals("viagem.posicao", primeiro.getMessageProperties().getReceivedRoutingKey());
        assertEquals("ViagemPosicaoAtualizadaEvent", primeiro.getMessageProperties().getHeader("event-type"));
        assertEquals("1.0", primeiro.getMessageProperties().getHeader("version"));
        assertNotNull(primeiro.getMessageProperties().getHeader("timestamp"));

        JsonNode corpoPrimeiro = JSON.readTree(primeiro.getBody());
        assertEquals(VIAGEM_ID, corpoPrimeiro.get("viagemId").asText());
        assertEquals(base, corpoPrimeiro.get("timestamp").asLong(),
                "o timestamp do aparelho tem de atravessar o stream ate o evento");
        assertEquals(88.0, corpoPrimeiro.get("bateria").asDouble());
        assertEquals(5.5, corpoPrimeiro.get("accuracy").asDouble(),
                "precisao do contrato Kafka vira accuracy no evento publicado");
        assertEquals("gps", corpoPrimeiro.get("provedor").asText());

        JsonNode corpoSegundo = JSON.readTree(segundo.getBody());
        assertEquals(base + 5_000, corpoSegundo.get("timestamp").asLong());
        assertTrue(corpoSegundo.get("timestamp").asLong() > corpoPrimeiro.get("timestamp").asLong(),
                "mesma chave: as posicoes da viagem chegam na ordem de leitura");

        var estado = trackingStateRepository.find("viagem:" + VIAGEM_ID).block();
        assertNotNull(estado, "o pipeline real precisa gravar o estado no Redis");
        assertEquals(base + 5_000, estado.getLastTimestamp());
        // O pipeline suaviza a posicao (Kalman): o estado tem de ter caminhado da primeira para a
        // segunda posicao, sem passar dela — o valor exato depende do fator de suavizacao.
        assertTrue(estado.getLastLat() < -23.5505 && estado.getLastLat() >= -23.5510,
                "estado deveria estar entre as duas posicoes publicadas, e veio " + estado.getLastLat());
    }

    @Test
    void posicaoDeAlunoDoKafkaChegaAoRabbit() throws Exception {
        long timestamp = System.currentTimeMillis();
        var evento = new PosicaoAlunoEvento(UUID.randomUUID().toString(), VIAGEM_ID, ALUNO_ID,
                -23.5520, -46.6350, timestamp, Instant.now().toString());

        kafkaTemplate.send(topicoAluno, ALUNO_ID, evento).get(10, TimeUnit.SECONDS);

        Message mensagem = esperarEventoDaViagem("aluno.posicao", timestamp);

        assertEquals("aluno.posicao", mensagem.getMessageProperties().getReceivedRoutingKey());
        assertEquals("AlunoPosicaoAtualizadaEvent", mensagem.getMessageProperties().getHeader("event-type"));

        JsonNode corpo = JSON.readTree(mensagem.getBody());
        assertEquals(ALUNO_ID, corpo.get("alunoId").asText());
        assertEquals(VIAGEM_ID, corpo.get("viagemId").asText());
        assertEquals(timestamp, corpo.get("timestamp").asLong());
        assertEquals("SEM_PONTO_DEFINIDO", corpo.get("status").asText(),
                "aluno sem embarque registrado no Redis");
    }

    @Test
    void reentregaDoMesmoEventoNaoAvancaOEstadoNemPublicaDeNovo() throws Exception {
        long base = System.currentTimeMillis();
        String eventId = UUID.randomUUID().toString();
        var evento = new PosicaoVeiculoEvento(eventId, VIAGEM_ID, -23.5505, -46.6333, base,
                null, null, null, Instant.now().toString());

        kafkaTemplate.send(topicoVeiculo, VIAGEM_ID, evento).get(10, TimeUnit.SECONDS);
        esperarEventoDaViagem("viagem.posicao", base);

        var estadoAntes = trackingStateRepository.find("viagem:" + VIAGEM_ID).block();
        assertNotNull(estadoAntes);
        assertEquals(base, estadoAntes.getLastTimestamp());

        // Mesmo eventId com timestamp MAIOR: passaria pela consistencia (que so rejeita timestamp
        // que nao avanca), entao e o marcador de idempotencia que tem de barrar o reprocessamento.
        long timestampRepetido = base + 60_000;
        var repetido = new PosicaoVeiculoEvento(eventId, VIAGEM_ID, -23.5599, -46.6399, timestampRepetido,
                null, null, null, Instant.now().toString());
        kafkaTemplate.send(topicoVeiculo, VIAGEM_ID, repetido).get(10, TimeUnit.SECONDS);

        long limite = System.currentTimeMillis() + 3_000;
        while (System.currentTimeMillis() < limite) {
            Message mensagem = rabbit.receive(FILA, 500);
            if (mensagem == null) {
                continue;
            }
            JsonNode corpo = JSON.readTree(mensagem.getBody());
            assertNotEquals(timestampRepetido, corpo.has("timestamp") ? corpo.get("timestamp").asLong() : -1,
                    "o evento repetido nao pode ter sido publicado de novo");
        }

        var estadoDepois = trackingStateRepository.find("viagem:" + VIAGEM_ID).block();
        assertNotNull(estadoDepois);
        assertEquals(base, estadoDepois.getLastTimestamp(),
                "evento com eventId ja processado nao pode avancar o estado");
        assertEquals(-23.5505, estadoDepois.getLastLat(), 1e-9);
    }

    /**
     * Le da fila de verificacao ate encontrar o evento da viagem com o timestamp esperado,
     * ignorando o que outras execucoes possam ter deixado no topico.
     */
    private Message esperarEventoDaViagem(String routingKey, long timestampEsperado) throws Exception {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (System.nanoTime() < limite) {
            Message mensagem = rabbit.receive(FILA, 500);
            if (mensagem == null) {
                continue;
            }
            assertEquals(routingKey, mensagem.getMessageProperties().getReceivedRoutingKey());
            JsonNode corpo = JSON.readTree(mensagem.getBody());
            if (corpo.hasNonNull("timestamp") && corpo.get("timestamp").asLong() == timestampEsperado) {
                return mensagem;
            }
        }
        fail("nenhum evento de " + routingKey + " com timestamp " + timestampEsperado + " chegou ao Rabbit");
        return null;
    }

    /** Os topicos deste teste sao criados aqui: o servico e consumidor e nao declara topico. */
    @TestConfiguration
    static class TopicosDeTeste {

        @Bean
        NewTopic topicoVeiculo(@Value("${tracking.topico-veiculo}") String topico) {
            return TopicBuilder.name(topico).partitions(3).replicas(1).build();
        }

        @Bean
        NewTopic topicoAluno(@Value("${tracking.topico-aluno}") String topico) {
            return TopicBuilder.name(topico).partitions(3).replicas(1).build();
        }
    }
}
