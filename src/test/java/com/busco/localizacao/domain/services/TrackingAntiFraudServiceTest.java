package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.Position;
import com.busco.localizacao.domain.vo.GeoPoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class TrackingAntiFraudServiceTest {

    private TrackingAntiFraudService service;

    @BeforeEach
    void setUp() {
        service = new TrackingAntiFraudService();
        ReflectionTestUtils.setField(service, "maxSpeed", 150.0);
        ReflectionTestUtils.setField(service, "maxDistanceJump", 500.0);
    }

    @Test
    void acceptsPositionBelowMaxSpeed() {
        Position position = new Position(new GeoPoint(-23.55, -46.63), 1000L);
        assertTrue(service.isValid(position));
    }

    @Test
    void acceptsPositionWithModerateSpeed() {
        Position position = new Position(new GeoPoint(-23.55, -46.63), 1000L);
        assertTrue(service.isValid(position));
    }

    @Test
    void acceptsDistanceBelowMaxJump() {
        assertTrue(service.isValidDistance(100));
    }

    @Test
    void acceptsDistanceAtMaxJump() {
        assertTrue(service.isValidDistance(500));
    }

    @Test
    void rejectsDistanceAboveMaxJump() {
        assertFalse(service.isValidDistance(600));
    }
}
