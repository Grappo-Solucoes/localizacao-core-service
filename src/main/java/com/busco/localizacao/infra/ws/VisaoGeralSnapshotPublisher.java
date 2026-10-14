package com.busco.localizacao.infra.ws;

import com.busco.localizacao.domain.services.VisaoGeralPublisher;
import com.busco.localizacao.domain.vo.VisaoGeral;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Service
public class VisaoGeralSnapshotPublisher implements VisaoGeralPublisher {

    private static final Logger log = LoggerFactory.getLogger(VisaoGeralSnapshotPublisher.class);

    private static final String DESTINATION = "/topic/visao-geral";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CAMPO_VOLATIL = "ultimaAtualizacao";

    private final SimpMessagingTemplate messagingTemplate;
    private final AtomicReference<String> lastFingerprint = new AtomicReference<>(null);
    private final AtomicLong lastSentAtMillis = new AtomicLong(0);
    private final long keepaliveMillis;

    public VisaoGeralSnapshotPublisher(
            SimpMessagingTemplate messagingTemplate,
            @Value("${app.live-view.keepalive-seconds:60}") long keepaliveSeconds) {
        this.messagingTemplate = messagingTemplate;
        this.keepaliveMillis = keepaliveSeconds * 1000L;
    }

    @Async
    @Override
    public void send(VisaoGeral visaoGeral) {
        send(visaoGeral, false);
    }

    @Async
    public void send(VisaoGeral visaoGeral, boolean force) {
        if (visaoGeral == null) {
            log.warn("Tentativa de enviar visão geral nula");
            return;
        }

        try {
            String fingerprint;
            try {
                fingerprint = fingerprint(visaoGeral);
            } catch (Exception e) {
                log.warn("Falha ao calcular fingerprint: {}", e.getMessage());
                fingerprint = "unserializable-" + System.nanoTime();
            }

            boolean changed = !fingerprint.equals(lastFingerprint.get());
            boolean keepaliveDue =
                    System.currentTimeMillis() - lastSentAtMillis.get() >= keepaliveMillis;

            if (!force && !changed && !keepaliveDue) {
                log.debug("Snapshot idêntico ao anterior, pulando envio WebSocket");
                return;
            }

            messagingTemplate.convertAndSend(DESTINATION, visaoGeral);
            lastFingerprint.set(fingerprint);
            lastSentAtMillis.set(System.currentTimeMillis());
            log.debug("WebSocket enviado - Viagens: {}, Detalhes: {}",
                visaoGeral.getPainel() != null && visaoGeral.getPainel().getViagens() != null ? visaoGeral.getPainel().getViagens() : 0,
                visaoGeral.getDetalhes() != null ? visaoGeral.getDetalhes().size() : 0);
        } catch (Exception e) {
            log.error("Erro ao enviar notificação: {}", e.getMessage(), e);
        }
    }

    private String fingerprint(VisaoGeral visaoGeral) throws Exception {
        JsonNode node = MAPPER.valueToTree(visaoGeral);

        if (node.has("detalhes") && node.get("detalhes").isArray()) {
            for (JsonNode detalhe : node.get("detalhes")) {
                if (detalhe instanceof ObjectNode objectNode && objectNode.has(CAMPO_VOLATIL)) {
                    objectNode.put(CAMPO_VOLATIL, 0L);
                }
            }
        }

        return MAPPER.writeValueAsString(node);
    }
}
