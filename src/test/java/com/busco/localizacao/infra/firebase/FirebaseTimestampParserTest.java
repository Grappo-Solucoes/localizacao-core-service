package com.busco.localizacao.infra.firebase;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FirebaseTimestampParserTest {

    private final FirebaseTimestampParser parser = new FirebaseTimestampParser();

    @Test
    void parseTimestampFromFormattedString() {
        String timestamp = "2023/10/13 18:00:00";

        long result = parser.parse(timestamp);

        assertTrue(result > 0);
    }

    @Test
    void parseLocationDTODefaults() {
        FirebaseLocationDTO dto = new FirebaseLocationDTO();

        assertEquals(0.0, dto.getLatitude());
        assertEquals(0.0, dto.getLongitude());
        assertNull(dto.getTimeStamp());
        assertNull(dto.getBatteryLevel());
        assertNull(dto.getAccuracy());
        assertNull(dto.getProvider());
    }
}
