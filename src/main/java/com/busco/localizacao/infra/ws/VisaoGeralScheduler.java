package com.busco.localizacao.infra.ws;

import com.busco.localizacao.domain.vo.Painel;
import com.busco.localizacao.domain.vo.VisaoGeral;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class VisaoGeralScheduler {

    private static final Logger log = LoggerFactory.getLogger(VisaoGeralScheduler.class);

    private final VisaoGeralSnapshotPublisher snapshotPublisher;
    private final VisaoGeralTopicTracker topicTracker;

    public VisaoGeralScheduler(
            VisaoGeralSnapshotPublisher snapshotPublisher,
            VisaoGeralTopicTracker topicTracker) {
        this.snapshotPublisher = snapshotPublisher;
        this.topicTracker = topicTracker;
    }

    @Scheduled(fixedDelay = 5000)
    public void gerarSnapshot() {
        if (!topicTracker.hasSubscribers()) {
            return;
        }

        try {
            VisaoGeral snapshot = new VisaoGeral(
                    Painel.of(0, 0, 0),
                    new ArrayList<>()
            );
            snapshotPublisher.send(snapshot);
        } catch (Exception e) {
            log.error("Erro no scheduler da Visão ao Vivo: {}", e.getMessage());
        }
    }
}
