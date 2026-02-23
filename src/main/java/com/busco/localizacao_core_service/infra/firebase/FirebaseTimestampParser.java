package com.busco.localizacao_core_service.infra.firebase;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@Component
public class FirebaseTimestampParser {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");

    public long parse(String value) {

        LocalDateTime dateTime =
                LocalDateTime.parse(value, FORMATTER);

        return dateTime
                .atZone(ZoneId.of("America/Sao_Paulo"))
                .toInstant()
                .toEpochMilli();
    }
}