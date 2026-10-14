package com.busco.localizacao.infra.firebase;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseTimestampParserTest {

    @Test
    void parseTimestampFromMillis() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("timestamp", 1697212800000L);

        long result = FirebaseTimestampParser.parse(raw);

        assertEquals(1697212800000L, result);
    }

    @Test
    void parseTimestampFromSeconds() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("timestamp", 1697212800L);

        long result = FirebaseTimestampParser.parse(raw);

        assertEquals(1697212800000L, result);
    }

    @Test
    void parseTimestampFromDouble() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("timestamp", 1697212800000.0);

        long result = FirebaseTimestampParser.parse(raw);

        assertEquals(1697212800000L, result);
    }

    @Test
    void parseTimestampMissing() {
        Map<String, Object> raw = new HashMap<>();

        long result = FirebaseTimestampParser.parse(raw);

        assertTrue(result > 0);
    }

    @Test
    void parseLocationDTO() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("lat", -23.5505);
        raw.put("lng", -46.6333);
        raw.put("timestamp", 1697212800000L);

        FirebaseLocationDTO dto = FirebaseLocationDTO.fromMap(raw);

        assertEquals(-23.5505, dto.latitude());
        assertEquals(-46.6333, dto.longitude());
        assertEquals(1697212800000L, dto.timestamp());
    }

    @Test
    void parseLocationDTOWithNullValues() {
        Map<String, Object> raw = new HashMap<>();

        FirebaseLocationDTO dto = FirebaseLocationDTO.fromMap(raw);

        assertEquals(0.0, dto.latitude());
        assertEquals(0.0, dto.longitude());
        assertTrue(dto.timestamp() > 0);
    }
}
