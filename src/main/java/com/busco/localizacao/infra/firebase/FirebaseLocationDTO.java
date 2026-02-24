package com.busco.localizacao.infra.firebase;

public class FirebaseLocationDTO {
    private double latitude;
    private double longitude;
    private String timeStamp;
    private Double batteryLevel;
    private Double accuracy;
    private String provider;

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public String getTimeStamp() {
        return timeStamp;
    }

    public Double getBatteryLevel() {
        return batteryLevel;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public String getProvider() {
        return provider;
    }
}
