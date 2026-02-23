package com.busco.localizacao.domain.entity;

public record TrackingResult(
        double latitude,
        double longitude,
        double speed,
        long timestamp
) {}