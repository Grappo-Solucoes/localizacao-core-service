package com.busco.localizacao.infra.firebase;

import com.busco.localizacao.domain.services.TrackingDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class FirebaseTrackingListener {

    private final ObjectMapper mapper;
    private final FirebaseTimestampParser parser;
    private final TrackingDomainService domainService;

    public FirebaseTrackingListener(
            ObjectMapper mapper,
            FirebaseTimestampParser parser,
            TrackingDomainService domainService
    ) {
        this.mapper = mapper;
        this.parser = parser;
        this.domainService = domainService;
    }

    public void onMotoristaEvent(
            String viagemId,
            Map<String, Object> raw
    ) {

        FirebaseLocationDTO dto =
                mapper.convertValue(raw, FirebaseLocationDTO.class);

        long timestamp =
                parser.parse(dto.getTimeStamp());

        domainService.processMotorista(
                viagemId,
                dto.getLatitude(),
                dto.getLongitude(),
                timestamp
        ).subscribe();
    }

    public void onAlunoEvent(
            String viagemId,
            String alunoId,
            Map<String, Object> raw
    ) {

        FirebaseLocationDTO dto =
                mapper.convertValue(raw, FirebaseLocationDTO.class);

        long timestamp =
                parser.parse(dto.getTimeStamp());

        domainService.processAluno(
                alunoId,
                viagemId,
                dto.getLatitude(),
                dto.getLongitude(),
                timestamp
        ).subscribe();
    }
}