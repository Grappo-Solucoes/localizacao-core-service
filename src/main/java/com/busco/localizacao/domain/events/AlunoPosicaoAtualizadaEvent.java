package com.busco.localizacao.domain.events;

public class AlunoPosicaoAtualizadaEvent {
    public String alunoId;
    public String viagemId;
    public double latitude;
    public double longitude;
    public long timestamp;

    public AlunoPosicaoAtualizadaEvent() {
    }



    public String getAlunoId() {
        return alunoId;
    }

    public void setAlunoId(String alunoId) {
        this.alunoId = alunoId;
    }

    public String getViagemId() {
        return viagemId;
    }

    public void setViagemId(String viagemId) {
        this.viagemId = viagemId;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
}
