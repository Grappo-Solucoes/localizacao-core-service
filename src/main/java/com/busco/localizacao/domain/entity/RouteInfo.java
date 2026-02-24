package com.busco.localizacao.domain.entity;

public class RouteInfo {
    private final double distance; // em km
    private final long etaSeconds; // em segundos

    public RouteInfo(double distance, long etaSeconds) {
        this.distance = distance;
        this.etaSeconds = etaSeconds;
    }

    public double distance() {
        return distance;
    }

    public long etaSeconds() {
        return etaSeconds;
    }

    public String getEtaFormatted() {
        long hours = etaSeconds / 3600;
        long minutes = (etaSeconds % 3600) / 60;
        long seconds = etaSeconds % 60;

        if (hours > 0) {
            return String.format("%dh %dm", hours, minutes);
        } else if (minutes > 0) {
            return String.format("%d min", minutes);
        } else {
            return String.format("%d seg", seconds);
        }
    }

    @Override
    public String toString() {
        return String.format("RouteInfo{distance=%.2f km, eta=%s}",
                distance, getEtaFormatted());
    }
}