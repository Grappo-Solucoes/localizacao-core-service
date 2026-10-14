package com.busco.localizacao.infra.kafka;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Prova, com broker real, que uma posicao que falha no pipeline nao trava a particao: depois da
 * retentativa limitada o registro vai para {@code <topico>.DLT}.
 *
 * <p>Grupo {@code integration}: exige Kafka (docker-compose.dev.yml) e Redis em localhost.</p>
 */
@SpringBootTest
@ActiveProfiles("test")
@Tag("integration")
@TestPropertySource(properties = {
        "spring.kafka.listener.auto-startup=true",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "tracking.tentativas=1"
})
class KafkaDltIntegrationTest {

    /** Topico proprio desta execucao: o DLT nao depende do historico de execucoes anteriores. */
    private static final String TOPICO_VEICULO = "tracking.posicao-veiculo.dlt-it-" + UUID.randomUUID();

    @DynamicPropertySource
    static void entradaIsolada(DynamicPropertyRegistry registry) {
        registry.add("tracking.topico-veiculo", () -> TOPICO_VEICULO);
        registry.add("spring.kafka.consumer.group-id", () -> "localizacao-core-dlt-" + UUID.randomUUID());
    }

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${tracking.topico-veiculo}")
    private String topicoVeiculo;

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @MockBean
    private TrackingOrchestratorService orchestrator;

    @Test
    void eventoQueFalhaNoPipelineVaiParaDeadLetter() throws Exception {
        when(orchestrator.processMotorista(anyString(), anyDouble(), anyDouble(), anyLong(), any(), any(), any()))
                .thenReturn(Mono.error(new IllegalStateException("falha injetada no pipeline")));

        var evento = new PosicaoVeiculoEvento(UUID.randomUUID().toString(), "viagem-dlt",
                -23.55, -46.63, 1_700_000_000_000L, null, null, null, Instant.now().toString());

        kafkaTemplate.send(topicoVeiculo, "viagem-dlt", evento).get(10, TimeUnit.SECONDS);

        // O topico de dead letter e persistente entre execucoes: procura o registro deste evento,
        // em vez de assumir que ele e o primeiro.
        String corpo = consumirDlt(topicoVeiculo + ".DLT", evento.eventId(), Duration.ofSeconds(30));

        assertNotNull(corpo, "nenhum registro com o eventId esperado chegou ao topico de dead letter");
        assertTrue(corpo.contains("viagem-dlt"), "o registro no DLT deveria carregar o evento original: " + corpo);
    }

    private String consumirDlt(String topico, String eventIdEsperado, Duration tempoLimite) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "dlt-" + UUID.randomUUID());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());

        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topico));
            long limite = System.nanoTime() + tempoLimite.toNanos();
            while (System.nanoTime() < limite) {
                for (var registro : consumer.poll(Duration.ofMillis(500))) {
                    if (registro.value() != null && registro.value().contains(eventIdEsperado)) {
                        return registro.value();
                    }
                }
            }
        }
        return null;
    }

    /** Os topicos deste teste sao criados aqui: o servico e consumidor e nao declara topico. */
    @TestConfiguration
    static class TopicosDeTeste {

        @Bean
        NewTopic topicoVeiculo(@Value("${tracking.topico-veiculo}") String topico) {
            return TopicBuilder.name(topico).partitions(1).replicas(1).build();
        }

        @Bean
        NewTopic topicoVeiculoDlt(@Value("${tracking.topico-veiculo}") String topico) {
            return TopicBuilder.name(topico + ".DLT").partitions(1).replicas(1).build();
        }
    }
}
