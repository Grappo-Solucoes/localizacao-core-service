package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.GeofenceEvent;
import com.busco.localizacao.domain.entity.PontoEmbarque;
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

    @Test
    void removeZonasSomenteDaViagemIndicada() {
        String pontoViagem1 = GeofencingService.zoneIdPontoRota("viagem-1", "ponto-1");
        String embarqueViagem1 = GeofencingService.zoneIdPontoEmbarque(
                "viagem-1", "aluno-1", PontoEmbarque.TipoEmbarque.EMBARQUE);
        String pontoViagem2 = GeofencingService.zoneIdPontoRota("viagem-2", "ponto-2");
        // viagem-1 e prefixo de viagem-10: a remocao nao pode atingir a outra viagem
        String pontoViagem10 = GeofencingService.zoneIdPontoRota("viagem-10", "ponto-10");
        service.registerZone(pontoViagem1, -23.55, -46.63, 100);
        service.registerZone(embarqueViagem1, -23.55, -46.63, 50);
        service.registerZone(pontoViagem2, -23.55, -46.63, 100);
        service.registerZone(pontoViagem10, -23.55, -46.63, 100);

        service.removerZonasDaViagem("viagem-1");

        StepVerifier.create(service.checkPosition("e", -23.55, -46.63, pontoViagem1)).verifyComplete();
        StepVerifier.create(service.checkPosition("e", -23.55, -46.63, embarqueViagem1)).verifyComplete();
        StepVerifier.create(service.checkPosition("e", -23.55, -46.63, pontoViagem2))
                .assertNext(event -> assertTrue(event.isInside())).verifyComplete();
        StepVerifier.create(service.checkPosition("e", -23.55, -46.63, pontoViagem10))
                .assertNext(event -> assertTrue(event.isInside())).verifyComplete();
    }
}
