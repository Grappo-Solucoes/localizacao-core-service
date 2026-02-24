package com.busco.localizacao.domain.events;

import java.util.Map;

public class ViagemPosicaoAtualizadaEvent {
    public String viagemId;
    public double latitude;
    public double longitude;
    public long timestamp;
    public double velocidade;
    public String endereco;
    public Double distanciaParaDestino;
    public Long etaSegundos;
    public String status; // "EM_ROTA", "PROXIMO", "CHEGOU", "PARADO"
    public Map<String, Object> metadata;
    public Double accuracy;
    public String provedor;
    public Object bateria;

    public String proximoPontoId;
    public String proximoPontoNome;
    public Double proximoPontoDistancia; // em metros
    public Long proximoPontoETA; // em segundos
    public String proximoPontoTipo; // TERMINAL, ESCOLA, PARADA


    public void addMetadata(String key, Object value) {
        if (metadata == null) {
            metadata = new java.util.HashMap<>();
        }
        metadata.put(key, value);
    }

    public boolean isProximoDoDestino(double destinoLat, double destinoLng, double raioMetros) {
        double distancia = calcularDistancia(latitude, longitude, destinoLat, destinoLng);
        return distancia <= raioMetros;
    }

    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        // Implementação de distância
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) *
                        Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return 6371000 * c;
    }

    public String getViagemId() {
        return viagemId;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public double getVelocidade() {
        return velocidade;
    }

    public String getEndereco() {
        return endereco;
    }

    public Double getDistanciaParaDestino() {
        return distanciaParaDestino;
    }

    public Long getEtaSegundos() {
        return etaSegundos;
    }

    public String getStatus() {
        return status;
    }

    public Map<String, Object> getMetadata() {
        return metadata;
    }

    public Double getAccuracy() {
        return accuracy;
    }

    public String getProvedor() {
        return provedor;
    }

    public Object getBateria() {
        return bateria;
    }

    public String getProximoPontoId() {
        return proximoPontoId;
    }

    public String getProximoPontoNome() {
        return proximoPontoNome;
    }

    public Double getProximoPontoDistancia() {
        return proximoPontoDistancia;
    }

    public Long getProximoPontoETA() {
        return proximoPontoETA;
    }

    public String getProximoPontoTipo() {
        return proximoPontoTipo;
    }
}