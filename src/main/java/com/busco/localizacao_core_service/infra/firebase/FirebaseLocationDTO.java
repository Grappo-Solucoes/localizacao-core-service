package com.busco.localizacao_core_service.infra.firebase;

public class FirebaseLocationDTO {
    private double latitude;
    private double longitude;
    private String timeStamp;

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getTimeStamp() {
        return timeStamp;
    }
}
