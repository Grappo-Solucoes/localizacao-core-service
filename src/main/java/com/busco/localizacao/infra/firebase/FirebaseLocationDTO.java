package com.busco.localizacao.infra.firebase;

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
