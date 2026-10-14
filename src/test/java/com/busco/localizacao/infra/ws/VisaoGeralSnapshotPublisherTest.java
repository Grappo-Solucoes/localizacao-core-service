package com.busco.localizacao.infra.ws;

import com.busco.localizacao.domain.vo.Detalhes;
import com.busco.localizacao.domain.vo.Painel;
import com.busco.localizacao.domain.vo.VisaoGeral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
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

    private VisaoGeral snapshot(int viagens) {
        return new VisaoGeral(Painel.of(viagens, 0, 0), new ArrayList<>());
    }

    @Test
    void enviaSnapshotQuandoNaoHaDuplicata() {
        VisaoGeral snapshot = snapshot(1);

        publisher.send(snapshot, true);

        verify(messagingTemplate, times(1)).convertAndSend(
                eq("/topic/visao-geral"),
                eq(snapshot)
        );
    }

    @Test
    void suprimeSnapshotDuplicado() {
        VisaoGeral snapshot = snapshot(1);

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

        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void enviaSnapshotComConteudoDiferente() {
        publisher.send(snapshot(1), true);
        publisher.send(snapshot(2), false);

        verify(messagingTemplate, times(2)).convertAndSend(
                anyString(),
                any(VisaoGeral.class)
        );
    }

    @Test
    void keepaliveReenviaSnapshotIdenticoQuandoJanelaExpira() {
        // keepalive 0s => janela sempre expirada
        VisaoGeralSnapshotPublisher keepalivePublisher =
                new VisaoGeralSnapshotPublisher(messagingTemplate, 0L);
        VisaoGeral snapshot = snapshot(1);

        keepalivePublisher.send(snapshot, true);
        keepalivePublisher.send(snapshot, false);

        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/visao-geral"),
                eq(snapshot)
        );
    }

    @Test
    void dedupIgnoraCampoVolatilUltimaAtualizacao() {
        Detalhes d1 = new Detalhes();
        d1.setTripId("viagem-1");
        d1.setUltimaAtualizacao(1000L);

        Detalhes d2 = new Detalhes();
        d2.setTripId("viagem-1");
        d2.setUltimaAtualizacao(2000L);

        publisher.send(new VisaoGeral(Painel.of(1, 0, 0), List.of(d1)), true);
        publisher.send(new VisaoGeral(Painel.of(1, 0, 0), List.of(d2)), false);

        // fingerprint ignora ultimaAtualizacao -> segundo envio é suprimido
        verify(messagingTemplate, times(1)).convertAndSend(anyString(), any(VisaoGeral.class));
    }

    @Test
    void forceIgnoraDedup() {
        VisaoGeral snapshot = snapshot(1);

        publisher.send(snapshot, true);
        publisher.send(snapshot, true);

        verify(messagingTemplate, times(2)).convertAndSend(
                eq("/topic/visao-geral"),
                eq(snapshot)
        );
    }

    @Test
    void publicaNoTopicoContratual() {
        publisher.send(snapshot(3), true);

        verify(messagingTemplate).convertAndSend(eq("/topic/visao-geral"), any(VisaoGeral.class));
    }

    @Test
    void snapshotComDetalhesPreservaPainelEDetalhes() {
        Detalhes detalhes = new Detalhes();
        detalhes.setTripId("viagem-9");
        detalhes.setPosicao(new Detalhes.Coordenadas("-23.55", "-46.63"));

        VisaoGeral snapshot = new VisaoGeral(Painel.of(1, 5, 2), List.of(detalhes));
        publisher.send(snapshot, true);

        verify(messagingTemplate).convertAndSend("/topic/visao-geral", snapshot);
    }
}
