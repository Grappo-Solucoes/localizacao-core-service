package com.busco.localizacao.domain.events;

import com.busco.localizacao.sk.ddd.DomainEvent;

import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Evento de posição atualizada da viagem.
 * Imutável: todos os campos são final e definidos no construtor.
 */
public final class ViagemPosicaoAtualizadaEvent implements DomainEvent {

    private final String viagemId;
    private final double latitude;
    private final double longitude;
    private final long timestamp;
    private final double velocidade;
    private final String endereco;
    private final Double distanciaParaDestino;
    private final Long etaSegundos;
    private final String status;
    private final Map<String, Object> metadata;
    private final Double accuracy;
    private final String provedor;
    private final Object bateria;
    private final String proximoPontoId;
    private final String proximoPontoNome;
    private final Double proximoPontoDistancia;
    private final Long proximoPontoETA;
    private final String proximoPontoTipo;
    private final Instant occurredOn;

    private ViagemPosicaoAtualizadaEvent(Builder builder) {
        this.viagemId = builder.viagemId;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.timestamp = builder.timestamp;
        this.velocidade = builder.velocidade;
        this.endereco = builder.endereco;
        this.distanciaParaDestino = builder.distanciaParaDestino;
        this.etaSegundos = builder.etaSegundos;
        this.status = builder.status;
        this.metadata = builder.metadata != null ? new HashMap<>(builder.metadata) : null;
        this.accuracy = builder.accuracy;
        this.provedor = builder.provedor;
        this.bateria = builder.bateria;
        this.proximoPontoId = builder.proximoPontoId;
        this.proximoPontoNome = builder.proximoPontoNome;
        this.proximoPontoDistancia = builder.proximoPontoDistancia;
        this.proximoPontoETA = builder.proximoPontoETA;
        this.proximoPontoTipo = builder.proximoPontoTipo;
        this.occurredOn = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getViagemId() { return viagemId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getTimestamp() { return timestamp; }
    public double getVelocidade() { return velocidade; }
    public String getEndereco() { return endereco; }
    public Double getDistanciaParaDestino() { return distanciaParaDestino; }
    public Long getEtaSegundos() { return etaSegundos; }
    public String getStatus() { return status; }
    public Map<String, Object> getMetadata() {
        return metadata != null ? Collections.unmodifiableMap(metadata) : null;
    }
    public Double getAccuracy() { return accuracy; }
    public String getProvedor() { return provedor; }
    public Object getBateria() { return bateria; }
    public String getProximoPontoId() { return proximoPontoId; }
    public String getProximoPontoNome() { return proximoPontoNome; }
    public Double getProximoPontoDistancia() { return proximoPontoDistancia; }
    public Long getProximoPontoETA() { return proximoPontoETA; }
    public String getProximoPontoTipo() { return proximoPontoTipo; }

    @Override
    public Instant getOccurredOn() { return occurredOn; }

    public boolean isProximoDoDestino(double destinoLat, double destinoLng, double raioMetros) {
        double distancia = calcularDistancia(latitude, longitude, destinoLat, destinoLng);
        return distancia <= raioMetros;
    }

    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) *
                        Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return 6371000 * c;
    }

    @Override
    public String toString() {
        return String.format(
                "ViagemPosicaoAtualizadaEvent{viagemId='%s', lat=%.6f, lng=%.6f, status=%s, velocidade=%.1f}",
                viagemId, latitude, longitude, status, velocidade
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ViagemPosicaoAtualizadaEvent that = (ViagemPosicaoAtualizadaEvent) o;
        return Objects.equals(viagemId, that.viagemId) && timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(viagemId, timestamp);
    }

    public static class Builder {
        private String viagemId;
        private double latitude;
        private double longitude;
        private long timestamp;
        private double velocidade;
        private String endereco;
        private Double distanciaParaDestino;
        private Long etaSegundos;
        private String status;
        private Map<String, Object> metadata;
        private Double accuracy;
        private String provedor;
        private Object bateria;
        private String proximoPontoId;
        private String proximoPontoNome;
        private Double proximoPontoDistancia;
        private Long proximoPontoETA;
        private String proximoPontoTipo;

        public Builder viagemId(String viagemId) { this.viagemId = viagemId; return this; }
        public Builder latitude(double latitude) { this.latitude = latitude; return this; }
        public Builder longitude(double longitude) { this.longitude = longitude; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder velocidade(double velocidade) { this.velocidade = velocidade; return this; }
        public Builder endereco(String endereco) { this.endereco = endereco; return this; }
        public Builder distanciaParaDestino(Double distanciaParaDestino) { this.distanciaParaDestino = distanciaParaDestino; return this; }
        public Builder etaSegundos(Long etaSegundos) { this.etaSegundos = etaSegundos; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder metadata(Map<String, Object> metadata) { this.metadata = metadata; return this; }
        public Builder accuracy(Double accuracy) { this.accuracy = accuracy; return this; }
        public Builder provedor(String provedor) { this.provedor = provedor; return this; }
        public Builder bateria(Object bateria) { this.bateria = bateria; return this; }
        public Builder proximoPontoId(String proximoPontoId) { this.proximoPontoId = proximoPontoId; return this; }
        public Builder proximoPontoNome(String proximoPontoNome) { this.proximoPontoNome = proximoPontoNome; return this; }
        public Builder proximoPontoDistancia(Double proximoPontoDistancia) { this.proximoPontoDistancia = proximoPontoDistancia; return this; }
        public Builder proximoPontoETA(Long proximoPontoETA) { this.proximoPontoETA = proximoPontoETA; return this; }
        public Builder proximoPontoTipo(String proximoPontoTipo) { this.proximoPontoTipo = proximoPontoTipo; return this; }

        public ViagemPosicaoAtualizadaEvent build() {
            return new ViagemPosicaoAtualizadaEvent(this);
        }
    }
}
