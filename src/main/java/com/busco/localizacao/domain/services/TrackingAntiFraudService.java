package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.Position;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class TrackingAntiFraudService {

    @Value("${tracking.antifraud.max-speed:150}")
    private double maxSpeed;

    @Value("${tracking.antifraud.max-distance-jump:500}")
    private double maxDistanceJump;

    public boolean isValid(Position position) {
        return position.getSpeed() <= maxSpeed;
    }

    public boolean isValidDistance(double distanceMeters) {
        return distanceMeters <= maxDistanceJump;
    }
}