package com.busco.localizacao.domain.entity;

public class GeofenceEvent {
    private final String entityId;
    private final String zoneId;
    private final boolean inside;
    private final double distanceToCenter;
    private final long timestamp;

    public GeofenceEvent(String entityId, String zoneId, boolean inside,
                         double distanceToCenter, long timestamp) {
        this.entityId = entityId;
        this.zoneId = zoneId;
        this.inside = inside;
        this.distanceToCenter = distanceToCenter;
        this.timestamp = timestamp;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getZoneId() {
        return zoneId;
    }

    public boolean isInside() {
        return inside;
    }

    public boolean isOutside() {
        return !inside;
    }

    public double getDistanceToCenter() {
        return distanceToCenter;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public String getEventType() {
        return inside ? "ENTROU_NA_ZONA" : "SAIU_DA_ZONA";
    }

    @Override
    public String toString() {
        return String.format("GeofenceEvent{entity='%s', zone='%s', type=%s, distance=%.1fm}",
                entityId, zoneId, getEventType(), distanceToCenter);
    }
}