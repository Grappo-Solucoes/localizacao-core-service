package com.busco.localizacao.domain.entity;

public class StopInfo {
    private final String entityId;
    private final long stopStartTime;
    private final long stopEndTime;
    private final long durationMs;

    public StopInfo(String entityId, long stopStartTime, long stopEndTime, long durationMs) {
        this.entityId = entityId;
        this.stopStartTime = stopStartTime;
        this.stopEndTime = stopEndTime;
        this.durationMs = durationMs;
    }

    public String getEntityId() {
        return entityId;
    }

    public long getStopStartTime() {
        return stopStartTime;
    }

    public long getStopEndTime() {
        return stopEndTime;
    }

    public long getDurationMs() {
        return durationMs;
    }

    public long getDurationMinutes() {
        return durationMs / 60000;
    }

    public boolean isLongStop() {
        return durationMs >= 300000; // 5 minutos ou mais
    }

    public String getStopType() {
        if (durationMs < 60000) { // menos de 1 minuto
            return "PARADA_RAPIDA";
        } else if (durationMs < 300000) { // entre 1 e 5 minutos
            return "PARADA_NORMAL";
        } else if (durationMs < 900000) { // entre 5 e 15 minutos
            return "PARADA_PROLOGADA";
        } else { // mais de 15 minutos
            return "PARADA_EXTENDIDA";
        }
    }

    @Override
    public String toString() {
        return String.format("StopInfo{entity='%s', duration=%d min, type=%s}",
                entityId, getDurationMinutes(), getStopType());
    }
}