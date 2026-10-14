package com.busco.localizacao;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.kafka.PosicaoAlunoKafkaListener;
import com.busco.localizacao.infra.kafka.PosicaoVeiculoKafkaListener;
import com.busco.localizacao.infra.redis.EventoProcessadoRepository;
import com.busco.localizacao.infra.ws.VisaoGeralScheduler;
import com.busco.localizacao.infra.ws.VisaoGeralSnapshotPublisher;
import com.busco.localizacao.infra.ws.VisaoGeralTopicTracker;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Teste de contexto Spring real.
 *
 * <p>Carrega o contexto completo com H2 e mocka a borda de processamento (o orquestrador) para
 * validar o wiring: entrada pelos listeners Kafka, repositorios, publisher STOMP e ObjectMapper.
 * Nao exige broker Kafka nem credencial de Firebase: no perfil de teste os listeners nao sobem
 * (`spring.kafka.listener.auto-startup=false`).</p>
 */
@SpringBootTest
@ActiveProfiles("test")
class LocalizacaoApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PosicaoVeiculoKafkaListener posicaoVeiculoKafkaListener;

    @Autowired
    private PosicaoAlunoKafkaListener posicaoAlunoKafkaListener;

    @Autowired
    private EventoProcessadoRepository eventoProcessadoRepository;

    @MockBean
    private TrackingOrchestratorService trackingOrchestratorService;

    @Test
    void contextoSpringCarrega() {
        assertNotNull(context);
        assertTrue(context.getBeanDefinitionCount() > 0);
    }

    @Test
    void beansDeEntradaPeloKafkaExistem() {
        assertNotNull(posicaoVeiculoKafkaListener);
        assertNotNull(posicaoAlunoKafkaListener);
        assertTrue(context.containsBean("posicaoVeiculoKafkaListener"));
        assertTrue(context.containsBean("posicaoAlunoKafkaListener"));
    }

    @Test
    void beanEventoProcessadoRepositoryExiste() {
        assertNotNull(eventoProcessadoRepository);
    }

    @Test
    void beanObjectMapperExiste() {
        assertNotNull(objectMapper);
    }

    @Test
    void beanVisaoGeralSchedulerExiste() {
        assertTrue(context.containsBean("visaoGeralScheduler"));
    }

    @Test
    void beanVisaoGeralSnapshotPublisherExiste() {
        assertTrue(context.containsBean("visaoGeralSnapshotPublisher"));
    }

    @Test
    void beanVisaoGeralTopicTrackerExiste() {
        assertTrue(context.containsBean("visaoGeralTopicTracker"));
    }
}
