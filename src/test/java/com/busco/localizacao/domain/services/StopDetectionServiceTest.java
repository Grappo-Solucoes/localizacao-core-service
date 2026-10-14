package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.StopInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StopDetectionServiceTest {

    private StopDetectionService service;

    @BeforeEach
    void setUp() {
        service = new StopDetectionService();
    }

    @Test
    void detectaParadaApos2MinutosParado() {
        String entityId = "viagem-1";
        long t0 = 1_000_000L;

        service.checkStop(entityId, 0.1, t0);
        StopInfo info = service.checkStop(entityId, 0.1, t0 + 120_000);

        assertNotNull(info);
        assertEquals(entityId, info.getEntityId());
        assertEquals(120_000, info.getDurationMs());
        assertEquals("PARADA_NORMAL", info.getStopType());
    }

    @Test
    void naoDetectaParadaApos1MinutoParado() {
        String entityId = "viagem-2";
        long t0 = 1_000_000L;

        service.checkStop(entityId, 0.1, t0);
        StopInfo info = service.checkStop(entityId, 0.1, t0 + 60_000);

        assertNull(info);
    }

    @Test
    void classificaParadaRapida() {
        StopInfo manual = new StopInfo("v", 0, 30_000, 30_000);
        assertEquals("PARADA_RAPIDA", manual.getStopType());
    }

    @Test
    void classificaParadaProlongada() {
        StopInfo info = new StopInfo("v", 0, 400_000, 400_000);
        assertEquals("PARADA_PROLOGADA", info.getStopType());
        assertTrue(info.isLongStop());
    }

    @Test
    void classificaParadaExtendida() {
        StopInfo info = new StopInfo("v", 0, 1_000_000, 1_000_000);
        assertEquals("PARADA_EXTENDIDA", info.getStopType());
        assertTrue(info.isLongStop());
    }

    @Test
    void resetaTrackerQuandoSeMove() {
        String entityId = "viagem-4";
        long t0 = 1_000_000L;

        service.checkStop(entityId, 0.1, t0);
        service.checkStop(entityId, 5.0, t0 + 30_000);
        StopInfo info = service.checkStop(entityId, 0.1, t0 + 90_000);

        assertNull(info);
    }
}
