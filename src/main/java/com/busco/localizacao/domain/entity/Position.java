package com.busco.localizacao.domain.entity;


import com.busco.localizacao.domain.vo.GeoPoint;

public class Position {

    private final GeoPoint point;
    private final double speed;
    private final long timestamp;

    public Position(
            GeoPoint point,
            double speed,
            long timestamp
    ) {
        this.point = point;
        this.speed = speed;
        this.timestamp = timestamp;
    }

    public Position(
            GeoPoint point,
            long timestamp
    ) {
        this.point = point;
        this.speed = 0;
        this.timestamp = timestamp;
    }
    public GeoPoint getPoint() { return point; }
    public double getSpeed() { return speed; }
    public long getTimestamp() { return timestamp; }
}