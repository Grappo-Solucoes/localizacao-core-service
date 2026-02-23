package com.busco.localizacao_core_service.domain.entity;

public record TrackingResult(
        double latitude,
        double longitude,
        double speed,
        long timestamp
) {}