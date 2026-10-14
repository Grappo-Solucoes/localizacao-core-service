package com.busco.localizacao.domain.events;

import com.busco.localizacao.sk.ddd.DomainEvent;

import java.time.Instant;
import java.util.Set;

/**
 * Evento de chegada do veículo em um ponto de embarque.
 * Publicado no Rabbit com routing key {@code viagem.chegada}.
 */
public class ViagemChegadaEvent implements DomainEvent {

    private final String viagemId;
    private final String pontoId;
    private final String pontoNome;
    private final double latitude;
    private final double longitude;
    private final long timestamp;
    private final Set<String> alunos;
    private final Instant occurredOn;

    public ViagemChegadaEvent(
            String viagemId,
            String pontoId,
            String pontoNome,
            double latitude,
            double longitude,
            long timestamp,
            Set<String> alunos
    ) {
        this.viagemId = viagemId;
        this.pontoId = pontoId;
        this.pontoNome = pontoNome;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timestamp = timestamp;
        this.alunos = alunos;
        this.occurredOn = Instant.ofEpochMilli(timestamp);
    }

    public String getViagemId() { return viagemId; }
    public String getPontoId() { return pontoId; }
    public String getPontoNome() { return pontoNome; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getTimestamp() { return timestamp; }
    public Set<String> getAlunos() { return alunos; }

    @Override
    public Instant getOccurredOn() { return occurredOn; }
}
