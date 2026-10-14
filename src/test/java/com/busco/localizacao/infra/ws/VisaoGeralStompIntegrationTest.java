package com.busco.localizacao.infra.ws;

import com.busco.localizacao.domain.vo.Painel;
import com.busco.localizacao.domain.vo.VisaoGeral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.security.Principal;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Teste de integração do publisher STOMP.
 *
 * Pipeline: inscrito → snapshot publicado → dedup → keepalive.
 *
 * Usa o {@link VisaoGeralScheduler} real com o {@link VisaoGeralTopicTracker} real
 * e o {@link SimpMessagingTemplate} mockado.
 */
class VisaoGeralStompIntegrationTest {

    private static final String TOPIC = "/topic/visao-geral";
    private static final String SESSION_ID = "sessao-stomp-1";
    private static final Principal PRINCIPAL = () -> "test-user";

    private SimpMessagingTemplate messagingTemplate;
    private VisaoGeralTopicTracker topicTracker;
    private VisaoGeralSnapshotPublisher snapshotPublisher;
    private VisaoGeralScheduler scheduler;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        topicTracker = new VisaoGeralTopicTracker();
        snapshotPublisher = new VisaoGeralSnapshotPublisher(messagingTemplate, 60L);
        scheduler = new VisaoGeralScheduler(snapshotPublisher, topicTracker);
    }

    private void simularInscricao(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(TOPIC);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());

        SessionSubscribeEvent event = new SessionSubscribeEvent(this, message, PRINCIPAL);
        topicTracker.onSubscribe(event);
    }

    private void simularDesinscricao(String sessionId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.UNSUBSCRIBE);
        accessor.setSessionId(sessionId);
        accessor.setDestination(TOPIC);
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());

        SessionUnsubscribeEvent event = new SessionUnsubscribeEvent(this, message, PRINCIPAL);
        topicTracker.onUnsubscribe(event);
    }

    private VisaoGeral snapshot(int viagens) {
        return new VisaoGeral(Painel.of(viagens, 0, 0), new ArrayList<>());
    }

    @Test
    void semInscritos_schedulerNaoPublica() {
        scheduler.gerarSnapshot();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void comInscrito_schedulerPublicaSnapshot() {
        simularInscricao(SESSION_ID);

        scheduler.gerarSnapshot();

        verify(messagingTemplate, times(1)).convertAndSend(eq(TOPIC), any(VisaoGeral.class));
    }

    @Test
    void aposDesinscricao_schedulerParaDePublicar() {
        simularInscricao(SESSION_ID);
        scheduler.gerarSnapshot();
        verify(messagingTemplate, times(1)).convertAndSend(eq(TOPIC), any(VisaoGeral.class));

        simularDesinscricao(SESSION_ID);
        scheduler.gerarSnapshot();

        // Não publica novamente
        verify(messagingTemplate, times(1)).convertAndSend(eq(TOPIC), any(VisaoGeral.class));
    }

    @Test
    void trackerContaMultiplosInscritos() {
        simularInscricao("sessao-1");
        simularInscricao("sessao-2");

        assertEquals(2, topicTracker.count());
        assertTrue(topicTracker.hasSubscribers());
    }

    @Test
    void trackerIgnoraOutrosTopicos() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setSessionId("sessao-outro");
        accessor.setDestination("/topic/outro");
        accessor.setLeaveMutable(true);
        Message<byte[]> message = MessageBuilder.createMessage(
                new byte[0], accessor.getMessageHeaders());

        topicTracker.onSubscribe(new SessionSubscribeEvent(this, message, PRINCIPAL));

        assertEquals(0, topicTracker.count());
        assertFalse(topicTracker.hasSubscribers());
    }

    @Test
    void dedup_noScheduler_suprimeSnapshotIdentico() {
        simularInscricao(SESSION_ID);

        // Primeiro snapshot: publica
        scheduler.gerarSnapshot();
        verify(messagingTemplate, times(1)).convertAndSend(eq(TOPIC), any(VisaoGeral.class));

        // Segundo snapshot idêntico: suprimido pelo dedup
        scheduler.gerarSnapshot();
        verify(messagingTemplate, times(1)).convertAndSend(eq(TOPIC), any(VisaoGeral.class));
    }

    @Test
    void keepalive_noScheduler_reenviaQuandoJanelaExpira() {
        // keepalive 0s => janela sempre expirada
        VisaoGeralSnapshotPublisher keepalivePublisher =
                new VisaoGeralSnapshotPublisher(messagingTemplate, 0L);
        VisaoGeralScheduler keepaliveScheduler =
                new VisaoGeralScheduler(keepalivePublisher, topicTracker);

        simularInscricao(SESSION_ID);

        keepaliveScheduler.gerarSnapshot();
        keepaliveScheduler.gerarSnapshot();

        verify(messagingTemplate, times(2)).convertAndSend(eq(TOPIC), any(VisaoGeral.class));
    }

    @Test
    void snapshotPublicadoContemPainelEDetalhes() {
        simularInscricao(SESSION_ID);

        scheduler.gerarSnapshot();

        var captor = org.mockito.ArgumentCaptor.forClass(VisaoGeral.class);
        verify(messagingTemplate).convertAndSend(eq(TOPIC), captor.capture());

        VisaoGeral publicado = captor.getValue();
        assertNotNull(publicado.getPainel());
        assertNotNull(publicado.getDetalhes());
        assertEquals(0, publicado.getPainel().getViagens());
    }

    @Test
    void trackerRemoveAposDesinscricao() {
        simularInscricao(SESSION_ID);
        assertEquals(1, topicTracker.count());

        simularDesinscricao(SESSION_ID);
        assertEquals(0, topicTracker.count());
        assertFalse(topicTracker.hasSubscribers());
    }
}
