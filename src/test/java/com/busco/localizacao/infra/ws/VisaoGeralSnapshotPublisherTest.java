package com.busco.localizacao.infra.ws;

import com.busco.localizacao.domain.vo.Painel;
import com.busco.localizacao.domain.vo.VisaoGeral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class VisaoGeralSnapshotPublisherTest {

    private SimpMessagingTemplate messagingTemplate;
    private VisaoGeralSnapshotPublisher publisher;

    @BeforeEach
    void setUp() {
        messagingTemplate = mock(SimpMessagingTemplate.class);
        publisher = new VisaoGeralSnapshotPublisher(messagingTemplate, 60L);
    }

    @Test
    void enviaSnapshotQuandoNaoHaDuplicata() {
        VisaoGeral snapshot = new VisaoGeral(
                Painel.of(1, 0, 0),
                new ArrayList<>()
        );

        publisher.send(snapshot, true);

        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/visao-geral"),
                eq(snapshot)
        );
    }

    @Test
    void suprimeSnapshotDuplicado() {
        VisaoGeral snapshot = new VisaoGeral(
                Painel.of(1, 0, 0),
                new ArrayList<>()
        );

        publisher.send(snapshot, true);
        publisher.send(snapshot, false);

        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/visao-geral"),
                eq(snapshot)
        );
    }

    @Test
    void ignoraSnapshotNulo() {
        publisher.send(null, true);

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(VisaoGeral.class));
    }

    @Test
    void enviaSnapshotComConteudoDiferente() {
        VisaoGeral snapshot1 = new VisaoGeral(
                Painel.of(1, 0, 0),
                new ArrayList<>()
        );

        VisaoGeral snapshot2 = new VisaoGeral(
                Painel.of(2, 0, 0),
                new ArrayList<>()
        );

        publisher.send(snapshot1, true);
        publisher.send(snapshot2, false);

        verify(messagingTemplate, times(2)).convertAndSend(
                anyString(),
                any(VisaoGeral.class)
        );
    }
}
