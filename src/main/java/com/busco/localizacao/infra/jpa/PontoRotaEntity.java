package com.busco.localizacao.infra.jpa;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ponto_rota", schema = "localizacao")
public class PontoRotaEntity {

    @Id
    private String id;

    @Column(name = "viagem_id", nullable = false)
    private String viagemId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private boolean visitado;

    @Column(name = "horario_chegada")
    private Instant horarioChegada;

    @Column(name = "horario_previsto")
    private Instant horarioPrevisto;

    @Column(name = "tempo_parada")
    private Integer tempoParada;

    @Column(name = "ordem")
    private int ordem;

    public PontoRotaEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getViagemId() { return viagemId; }
    public void setViagemId(String viagemId) { this.viagemId = viagemId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public boolean isVisitado() { return visitado; }
    public void setVisitado(boolean visitado) { this.visitado = visitado; }
    public Instant getHorarioChegada() { return horarioChegada; }
    public void setHorarioChegada(Instant horarioChegada) { this.horarioChegada = horarioChegada; }
    public Instant getHorarioPrevisto() { return horarioPrevisto; }
    public void setHorarioPrevisto(Instant horarioPrevisto) { this.horarioPrevisto = horarioPrevisto; }
    public Integer getTempoParada() { return tempoParada; }
    public void setTempoParada(Integer tempoParada) { this.tempoParada = tempoParada; }
    public int getOrdem() { return ordem; }
    public void setOrdem(int ordem) { this.ordem = ordem; }
}
