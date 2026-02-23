package com.busco.localizacao_core_service.domain.services;

import org.springframework.stereotype.Component;

@Component
public class KalmanFilter {

    private static final double ALPHA = 0.2;

    public double smooth(double previous, double current) {
        return previous + ALPHA * (current - previous);
    }
}