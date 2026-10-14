package com.busco.localizacao.infra.firebase;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FirebaseTrackingListener {

    private static final Logger log = LoggerFactory.getLogger(FirebaseTrackingListener.class);

    private final ObjectMapper mapper;
    private final FirebaseTimestampParser parser;
    private final TrackingOrchestratorService domainService;

    public FirebaseTrackingListener(
            ObjectMapper mapper,
            FirebaseTimestampParser parser,
            TrackingOrchestratorService domainService
    ) {
        this.mapper = mapper;
        this.parser = parser;
        this.domainService = domainService;
    }

    public void onMotoristaEvent(
            String viagemId,
            Map<String, Object> raw
    ) {

        FirebaseLocationDTO dto;
        long timestamp;
        try {
            dto = mapper.convertValue(raw, FirebaseLocationDTO.class);
            timestamp = parser.parse(dto.getTimeStamp());
        } catch (RuntimeException e) {
            // Spec localizacao/streaming: payload sem timestamp valido e descartado com
            // registro de erro, sem emitir evento (e sem derrubar o thread do listener).
            log.warn("Posicao do motorista descartada (viagem {}): payload invalido: {}",
                    viagemId, e.getMessage());
            return;
        }

        domainService.processMotorista(
                viagemId,
                dto.getLatitude(),
                dto.getLongitude(),
                timestamp,
                dto.getBatteryLevel(),
                dto.getAccuracy(),
                dto.getProvider()
        ).subscribe(null, e -> log.warn("Falha ao processar posicao do motorista (viagem {}): {}",
                viagemId, e.getMessage()));
    }

    public void onAlunoEvent(
            String viagemId,
            String alunoId,
            Map<String, Object> raw
    ) {

        FirebaseLocationDTO dto;
        long timestamp;
        try {
            dto = mapper.convertValue(raw, FirebaseLocationDTO.class);
            timestamp = parser.parse(dto.getTimeStamp());
        } catch (RuntimeException e) {
            log.warn("Posicao do aluno {} descartada (viagem {}): payload invalido: {}",
                    alunoId, viagemId, e.getMessage());
            return;
        }

        domainService.processAluno(
                alunoId,
                viagemId,
                dto.getLatitude(),
                dto.getLongitude(),
                timestamp
        ).subscribe(null, e -> log.warn("Falha ao processar posicao do aluno {} (viagem {}): {}",
                alunoId, viagemId, e.getMessage()));
    }
}
