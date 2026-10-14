package com.busco.localizacao.e2e;

import com.busco.localizacao.domain.vo.Painel;
import com.busco.localizacao.domain.vo.VisaoGeral;
import com.busco.localizacao.infra.ws.VisaoGeralScheduler;
import com.busco.localizacao.infra.ws.VisaoGeralSnapshotPublisher;
import com.busco.localizacao.infra.ws.VisaoGeralTopicTracker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Teste end-to-end do publisher STOMP da Visão ao Vivo:
 * inscrito -> snapshot publicado -> dedup -> keepalive.
 */
class StreamingStompE2ETest {

    private static final String DESTINO = "/topic/visao-geral";

    private SimpMessagingTemplate messagingTemplate;
    private VisaoGeralTopicTracker topicTracker;
    private VisaoGeralSnapshotPublisher publisher;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        topicTracker = new VisaoGeralTopicTracker();
        publisher = new VisaoGeralSnapshotPublisher(messagingTemplate, 60L);
    }

    private Message<byte[]> mensagemStomp(StompCommand command, String sessionId, String destination) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        accessor.setSessionId(sessionId);
        if (destination != null) {
            accessor.setDestination(destination);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    private void inscrever(String sessionId) {
        topicTracker.onSubscribe(new SessionSubscribeEvent(
                this, mensagemStomp(StompCommand.SUBSCRIBE, sessionId, DESTINO)));
    }

    private VisaoGeral snapshot(int viagens) {
        return new VisaoGeral(Painel.of(viagens, 0, 0), new ArrayList<>());
    }

    @Test
    void semInscritos_trackerNaoTemAssinantes() {
        assert !topicTracker.hasSubscribers();
    }

    @Test
    void inscricaoNoTopico_contabilizaAssinante() {
        inscrever("sessao-1");

        assert topicTracker.hasSubscribers();
        assert topicTracker.count() == 1;
    }

    @Test
    void inscricaoEmOutroTopico_naoContabiliza() {
        topicTracker.onSubscribe(new SessionSubscribeEvent(
                this, mensagemStomp(StompCommand.SUBSCRIBE, "sessao-1", "/topic/outro")));

        assert !topicTracker.hasSubscribers();
    }

    @Test
    void schedulerComInscrito_publicaSnapshot() {
        inscrever("sessao-1");
        VisaoGeralScheduler scheduler = new VisaoGeralScheduler(publisher, topicTracker);

        scheduler.gerarSnapshot();

        verify(messagingTemplate).convertAndSend(eq(DESTINO), any(Object.class));
    }

    @Test
    void schedulerSemInscrito_naoPublicaSnapshot() {
        VisaoGeralScheduler scheduler = new VisaoGeralScheduler(publisher, topicTracker);

        scheduler.gerarSnapshot();

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void snapshotDuplicadoSuprimido_snapshotNovoPublicado() {
        publisher.send(snapshot(1), false);
        publisher.send(snapshot(1), false); // duplicado: suprimido
        publisher.send(snapshot(2), false); // diferente: publicado

        verify(messagingTemplate, times(2)).convertAndSend(eq(DESTINO), any(Object.class));
    }

    @Test
    void keepaliveReenviaSnapshotIdenticoAposJanela() {
        VisaoGeralSnapshotPublisher keepalivePublisher =
                new VisaoGeralSnapshotPublisher(messagingTemplate, 0L);
        VisaoGeral snapshot = snapshot(1);

        keepalivePublisher.send(snapshot, false);
        keepalivePublisher.send(snapshot, false);

        // janela de keepalive expirada: reenvia mesmo sem mudança
        verify(messagingTemplate, times(2)).convertAndSend(eq(DESTINO), any(Object.class));
    }

    @Test
    void unsubscribeRemoveAssinante() {
        inscrever("sessao-1");
        topicTracker.onUnsubscribe(new SessionUnsubscribeEvent(
                this, mensagemStomp(StompCommand.UNSUBSCRIBE, "sessao-1", DESTINO)));

        assert !topicTracker.hasSubscribers();
    }

    @Test
    void disconnectRemoveAssinante() {
        inscrever("sessao-1");
        topicTracker.onDisconnect(new SessionDisconnectEvent(
                this,
                mensagemStomp(StompCommand.DISCONNECT, "sessao-1", null),
                "sessao-1",
                CloseStatus.NORMAL));

        assert !topicTracker.hasSubscribers();
    }

    @Test
    void multiplasSessoes_contabilizadasIndividualmente() {
        inscrever("sessao-1");
        inscrever("sessao-2");

        assert topicTracker.count() == 2;
    }

    @Test
    void disconnectDeUmaSessaoMantemOutra() {
        inscrever("sessao-1");
        inscrever("sessao-2");

        topicTracker.onDisconnect(new SessionDisconnectEvent(
                this,
                mensagemStomp(StompCommand.DISCONNECT, "sessao-1", null),
                "sessao-1",
                CloseStatus.NORMAL));

        assert topicTracker.count() == 1;
        assert topicTracker.hasSubscribers();
    }

    @Test
    void snapshotNuloIgnorado() {
        publisher.send(null, true);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void payloadContratual_temPainelEDetalhes() {
        publisher.send(snapshot(3), true);

        verify(messagingTemplate).convertAndSend(eq(DESTINO), (Object) argThat(arg -> {
            VisaoGeral visao = (VisaoGeral) arg;
            return visao.getPainel() != null && visao.getDetalhes() != null;
        }));
    }
}
