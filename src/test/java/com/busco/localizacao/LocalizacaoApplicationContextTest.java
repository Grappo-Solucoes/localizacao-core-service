package com.busco.localizacao;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.firebase.FirebaseTimestampParser;
import com.busco.localizacao.infra.firebase.FirebaseTrackingListener;
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

import static org.junit.jupiter.api.Assertions.*;

/**
 * Teste de contexto Spring real.
 *
 * Carrega o contexto completo da aplicação com H2 e mocks das dependências
 * externas (Redis, Rabbit, Firebase) para validar wiring de beans.
 */
@SpringBootTest
@ActiveProfiles("test")
class LocalizacaoApplicationContextTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FirebaseTimestampParser firebaseTimestampParser;

    @MockBean
    private TrackingOrchestratorService trackingOrchestratorService;

    @Test
    void contextoSpringCarrega() {
        assertNotNull(context);
        assertTrue(context.getBeanDefinitionCount() > 0);
    }

    @Test
    void beanFirebaseTimestampParserExiste() {
        assertNotNull(firebaseTimestampParser);
    }

    @Test
    void beanObjectMapperExiste() {
        assertNotNull(objectMapper);
    }

    @Test
    void beanFirebaseTrackingListenerExiste() {
        assertTrue(context.containsBean("firebaseTrackingListener"));
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

    @Test
    void parserIntegradoAoContextoFunciona() {
        long timestamp = firebaseTimestampParser.parse("2026/10/14 10:30:00");
        assertTrue(timestamp > 0);
    }
}
