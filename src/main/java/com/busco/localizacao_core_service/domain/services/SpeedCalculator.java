package com.busco.localizacao_core_service.domain.services;

import org.springframework.stereotype.Component;

@Component
public class SpeedCalculator {

    private static final double EARTH_RADIUS = 6371000;

    public double calculate(
            double lat1,
            double lng1,
            double lat2,
            double lng2,
            long timeMs
    ) {

        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);

        double a =
                Math.sin(dLat/2) * Math.sin(dLat/2)
                        + Math.cos(Math.toRadians(lat1))
                        * Math.cos(Math.toRadians(lat2))
                        * Math.sin(dLng/2) * Math.sin(dLng/2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        double distance = EARTH_RADIUS * c;

        double seconds = timeMs / 1000.0;

        if (seconds == 0) return 0;

        return (distance / seconds) * 3.6; // km/h
    }
}