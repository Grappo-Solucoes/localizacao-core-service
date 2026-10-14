package com.busco.localizacao.infra.firebase;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class FirebaseTrackingListenerTest {

    private FirebaseTimestampParser parser;
    private TrackingOrchestratorService domainService;
    private FirebaseTrackingListener listener;

    @BeforeEach
    void setUp() {
        parser = new FirebaseTimestampParser();
        domainService = mock(TrackingOrchestratorService.class);
        when(domainService.processMotorista(
                anyString(), anyDouble(), anyDouble(), anyLong(),
                any(), any(), any()
        )).thenReturn(Mono.empty());
        when(domainService.processAluno(
                anyString(), anyString(), anyDouble(), anyDouble(), anyLong()
        )).thenReturn(Mono.empty());

        listener = new FirebaseTrackingListener(new ObjectMapper(), parser, domainService);
    }

    @Test
    void onMotoristaEvent_chamaProcessMotoristaComDadosDoFirebase() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", -23.5505);
        raw.put("longitude", -46.6333);
        raw.put("timeStamp", "2026/10/14 10:30:00");
        raw.put("batteryLevel", 0.85);
        raw.put("accuracy", 5.0);
        raw.put("provider", "gps");

        listener.onMotoristaEvent("viagem-1", raw);

        verify(domainService).processMotorista(
                eq("viagem-1"),
                eq(-23.5505),
                eq(-46.6333),
                anyLong(),
                eq(0.85),
                eq(5.0),
                eq("gps")
        );
    }

    @Test
    void onAlunoEvent_chamaProcessAlunoComDadosDoFirebase() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", -23.5600);
        raw.put("longitude", -46.6400);
        raw.put("timeStamp", "2026/10/14 10:31:00");
        raw.put("batteryLevel", 0.75);
        raw.put("accuracy", 8.0);
        raw.put("provider", "network");

        listener.onAlunoEvent("viagem-1", "aluno-1", raw);

        verify(domainService).processAluno(
                eq("aluno-1"),
                eq("viagem-1"),
                eq(-23.5600),
                eq(-46.6400),
                anyLong()
        );
    }

    @Test
    void onMotoristaEvent_converteTimestampFirebaseParaEpochMilli() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", -23.5505);
        raw.put("longitude", -46.6333);
        raw.put("timeStamp", "2026/10/14 10:30:00");

        listener.onMotoristaEvent("viagem-1", raw);

        ArgumentCaptor<Long> captor = ArgumentCaptor.forClass(Long.class);
        verify(domainService).processMotorista(
                anyString(), anyDouble(), anyDouble(),
                captor.capture(), any(), any(), any()
        );

        long esperado = parser.parse("2026/10/14 10:30:00");
        assertEquals(esperado, captor.getValue());
        assertTrue(captor.getValue() > 0);
    }

    @Test
    void onMotoristaEvent_preservaCoordenadas() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", -23.5505);
        raw.put("longitude", -46.6333);
        raw.put("timeStamp", "2026/10/14 10:30:00");

        listener.onMotoristaEvent("viagem-1", raw);

        ArgumentCaptor<Double> latCaptor = ArgumentCaptor.forClass(Double.class);
        ArgumentCaptor<Double> lngCaptor = ArgumentCaptor.forClass(Double.class);
        verify(domainService).processMotorista(
                anyString(), latCaptor.capture(), lngCaptor.capture(),
                anyLong(), any(), any(), any()
        );

        assertEquals(-23.5505, latCaptor.getValue());
        assertEquals(-46.6333, lngCaptor.getValue());
    }

    @Test
    void onMotoristaEvent_aceitaCamposOpcionaisAusentes() {
        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", -23.5505);
        raw.put("longitude", -46.6333);
        raw.put("timeStamp", "2026/10/14 10:30:00");

        listener.onMotoristaEvent("viagem-1", raw);

        verify(domainService).processMotorista(
                eq("viagem-1"),
                eq(-23.5505),
                eq(-46.6333),
                anyLong(),
                isNull(),
                isNull(),
                isNull()
        );
    }
}
