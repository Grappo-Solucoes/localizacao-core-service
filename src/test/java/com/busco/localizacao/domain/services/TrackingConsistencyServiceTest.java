package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.Position;
import com.busco.localizacao.domain.vo.GeoPoint;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TrackingConsistencyServiceTest {

    private final TrackingConsistencyService service = new TrackingConsistencyService();

    @Test
    void acceptsFirstPosition() {
        Position first = new Position(new GeoPoint(-23.55, -46.63), 1000L);
        assertTrue(service.isValid(null, first));
    }

    @Test
    void acceptsPositionWithLaterTimestamp() {
        Position previous = new Position(new GeoPoint(-23.55, -46.63), 1000L);
        Position current = new Position(new GeoPoint(-23.56, -46.64), 2000L);
        assertTrue(service.isValid(previous, current));
    }

    @Test
    void rejectsPositionWithSameTimestamp() {
        Position previous = new Position(new GeoPoint(-23.55, -46.63), 1000L);
        Position current = new Position(new GeoPoint(-23.56, -46.64), 1000L);
        assertFalse(service.isValid(previous, current));
    }

    @Test
    void rejectsPositionWithEarlierTimestamp() {
        Position previous = new Position(new GeoPoint(-23.55, -46.63), 2000L);
        Position current = new Position(new GeoPoint(-23.56, -46.64), 1000L);
        assertFalse(service.isValid(previous, current));
    }
}
