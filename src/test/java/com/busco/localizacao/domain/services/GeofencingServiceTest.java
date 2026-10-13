package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.GeofenceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import reactor.test.StepVerifier;

class GeofencingServiceTest {

    private GeofencingService service;

    @BeforeEach
    void setUp() {
        service = new GeofencingService();
    }

    @Test
    void detectaEntradaNaZona() {
        service.registerZone("zona-1", -23.5505, -46.6333, 300);

        StepVerifier.create(service.checkPosition("entidade-1", -23.5505, -46.6333, "zona-1"))
                .assertNext(event -> {
                    assertTrue(event.isInside());
                    assertEquals("ENTROU_NA_ZONA", event.getEventType());
                    assertEquals(0.0, event.getDistanceToCenter(), 0.001);
                })
                .verifyComplete();
    }

    @Test
    void detectaSaidaDaZona() {
        service.registerZone("zona-2", -23.5505, -46.6333, 300);

        StepVerifier.create(service.checkPosition("entidade-2", -23.6000, -46.7000, "zona-2"))
                .assertNext(event -> {
                    assertFalse(event.isInside());
                    assertEquals("SAIU_DA_ZONA", event.getEventType());
                    assertTrue(event.getDistanceToCenter() > 300);
                })
                .verifyComplete();
    }

    @Test
    void retornaEmptyParaZonaNaoRegistrada() {
        StepVerifier.create(service.checkPosition("entidade-3", -23.5, -46.6, "zona-inexistente"))
                .verifyComplete();
    }

    @Test
    void detectaPosicaoNoLimiteDaZona() {
        service.registerZone("zona-4", -23.5505, -46.6333, 300);

        // Posição dentro do raio (200m do centro)
        // ~0.0018 graus de latitude ≈ 200m
        double latLimite = -23.5505 + 0.0018;

        StepVerifier.create(service.checkPosition("entidade-4", latLimite, -46.6333, "zona-4"))
                .assertNext(event -> {
                    assertTrue(event.isInside());
                    assertTrue(event.getDistanceToCenter() <= 300);
                })
                .verifyComplete();
    }
}
