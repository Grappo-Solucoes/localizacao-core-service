package com.busco.localizacao.infra.ws;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;
import org.springframework.web.socket.messaging.SessionUnsubscribeEvent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class VisaoGeralTopicTracker {

    private static final Logger log = LoggerFactory.getLogger(VisaoGeralTopicTracker.class);

    public static final String TOPIC_VISAO_GERAL = "/topic/visao-geral";

    private final Map<String, AtomicInteger> subscriptionsBySession = new ConcurrentHashMap<>();

    public boolean hasSubscribers() {
        return count() > 0;
    }

    public int count() {
        return subscriptionsBySession.values().stream()
                .mapToInt(AtomicInteger::get)
                .sum();
    }

    @EventListener
    public void onSubscribe(SessionSubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String destination = accessor.getDestination();
        String sessionId = accessor.getSessionId();

        if (sessionId == null || destination == null || !destination.equals(TOPIC_VISAO_GERAL)) {
            return;
        }

        subscriptionsBySession
                .computeIfAbsent(sessionId, id -> new AtomicInteger(0))
                .incrementAndGet();
        log.debug("Inscritos em {}: {}", TOPIC_VISAO_GERAL, count());
    }

    @EventListener
    public void onUnsubscribe(SessionUnsubscribeEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();

        if (sessionId == null) {
            return;
        }

        AtomicInteger counter = subscriptionsBySession.get(sessionId);
        if (counter != null && counter.decrementAndGet() <= 0) {
            subscriptionsBySession.remove(sessionId);
        }
        log.debug("Inscritos em {}: {}", TOPIC_VISAO_GERAL, count());
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();

        if (sessionId != null && subscriptionsBySession.remove(sessionId) != null) {
            log.debug("Inscritos em {}: {}", TOPIC_VISAO_GERAL, count());
        }
    }
}
