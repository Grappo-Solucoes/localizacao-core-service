package com.busco.localizacao.domain.entity;

public class TrackingState {

    private double lastLat;
    private double lastLng;
    private long lastTimestamp;
    private double lastSpeed;

    public TrackingState(
            double lastLat,
            double lastLng,
            long lastTimestamp,
            double lastSpeed
    ) {
        this.lastLat = lastLat;
        this.lastLng = lastLng;
        this.lastTimestamp = lastTimestamp;
        this.lastSpeed = lastSpeed;
    }

    public double getLastLat() { return lastLat; }
    public double getLastLng() { return lastLng; }
    public long getLastTimestamp() { return lastTimestamp; }
    public double getLastSpeed() { return lastSpeed; }
}