package com.busco.localizacao.domain.services;

import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class BatteryOptimizationService {

    private final Map<String, TrackingConfig> configs = new ConcurrentHashMap<>();

    public TrackingConfig getConfigForEntity(String entityId, double batteryLevel) {
        return configs.computeIfAbsent(entityId, k ->
                new TrackingConfig(calculateUpdateInterval(batteryLevel))
        );
    }

    private long calculateUpdateInterval(double batteryLevel) {
        if (batteryLevel < 15) {
            return 30000; // 30 segundos - modo economia extrema
        } else if (batteryLevel < 30) {
            return 15000; // 15 segundos - modo economia
        } else if (batteryLevel < 50) {
            return 10000; // 10 segundos - normal
        } else {
            return 5000; // 5 segundos - tempo real
        }
    }

    public record TrackingConfig(long updateIntervalMs) {
        public long batteryLevel() {
            return 0;
        }
    }
}