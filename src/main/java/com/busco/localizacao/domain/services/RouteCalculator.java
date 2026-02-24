package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.RouteInfo;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class RouteCalculator {

    private static final double AVERAGE_SPEED = 30.0; // km/h em área urbana

    public Mono<RouteInfo> calculateETA(
            double currentLat,
            double currentLng,
            double destinationLat,
            double destinationLng,
            double currentSpeed
    ) {
        double distance = calculateHaversineDistance(
                currentLat, currentLng, destinationLat, destinationLng
        );

        // Usa velocidade atual se for maior que zero, senão usa média
        double speedToUse = currentSpeed > 5 ? currentSpeed : AVERAGE_SPEED;

        // Evita divisão por zero
        if (speedToUse <= 0) speedToUse = AVERAGE_SPEED;

        double etaHours = distance / speedToUse;
        long etaSeconds = (long) (etaHours * 3600);

        return Mono.just(new RouteInfo(distance, etaSeconds));
    }

    private double calculateHaversineDistance(
            double lat1, double lon1, double lat2, double lon2) {
        // Implementação similar ao SpeedCalculator
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) *
                        Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return 6371 * c; // distância em km
    }
}