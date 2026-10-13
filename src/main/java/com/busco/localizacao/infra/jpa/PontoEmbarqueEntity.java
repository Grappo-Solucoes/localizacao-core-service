package com.busco.localizacao.infra.jpa;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "ponto_embarque", schema = "localizacao")
public class PontoEmbarqueEntity {

    @Id
    private String id;

    @Column(name = "viagem_id", nullable = false)
    private String viagemId;

    @Column(name = "aluno_id", nullable = false)
    private String alunoId;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private double latitude;

    @Column(nullable = false)
    private double longitude;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false)
    private boolean realizado;

    @Column(name = "horario_realizado")
    private Instant horarioRealizado;

    public PontoEmbarqueEntity() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getViagemId() { return viagemId; }
    public void setViagemId(String viagemId) { this.viagemId = viagemId; }
    public String getAlunoId() { return alunoId; }
    public void setAlunoId(String alunoId) { this.alunoId = alunoId; }
    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public boolean isRealizado() { return realizado; }
    public void setRealizado(boolean realizado) { this.realizado = realizado; }
    public Instant getHorarioRealizado() { return horarioRealizado; }
    public void setHorarioRealizado(Instant horarioRealizado) { this.horarioRealizado = horarioRealizado; }
}
