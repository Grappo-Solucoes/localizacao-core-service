package com.busco.localizacao_core_service.domain.services;

import com.busco.localizacao_core_service.domain.entity.Position;
import org.springframework.stereotype.Service;

@Service
public class TrackingAntiFraudService {

    private static final double MAX_SPEED = 150;

    public boolean isValid(Position position) {

        return position.getSpeed() <= MAX_SPEED;
    }
}