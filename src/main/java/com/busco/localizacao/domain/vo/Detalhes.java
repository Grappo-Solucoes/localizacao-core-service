package com.busco.localizacao.domain.vo;

import java.util.List;

/**
 * Detalhes de uma viagem na Visão Geral.
 */
public class Detalhes {
    private String tripId;
    private String motoristaNome;
    private String monitorNome;
    private String placa;
    private Integer viagemCodigo;
    private String numFrota;
    private List<Coordenadas> rota;
    private Coordenadas posicao;
    private Integer alunos;
    private Integer ocorrencias;
    private Long ultimaAtualizacao;
    private String numRota;
    private Integer lotacao;
    private Integer alunosABordo;
    private Integer faltantes;
    private String proximaParada;
    private String previsaoProximaParada;

    public Detalhes() {}

    public String getTripId() { return tripId; }
    public void setTripId(String tripId) { this.tripId = tripId; }
    public String getMotoristaNome() { return motoristaNome; }
    public void setMotoristaNome(String motoristaNome) { this.motoristaNome = motoristaNome; }
    public String getMonitorNome() { return monitorNome; }
    public void setMonitorNome(String monitorNome) { this.monitorNome = monitorNome; }
    public String getPlaca() { return placa; }
    public void setPlaca(String placa) { this.placa = placa; }
    public Integer getViagemCodigo() { return viagemCodigo; }
    public void setViagemCodigo(Integer viagemCodigo) { this.viagemCodigo = viagemCodigo; }
    public String getNumFrota() { return numFrota; }
    public void setNumFrota(String numFrota) { this.numFrota = numFrota; }
    public List<Coordenadas> getRota() { return rota; }
    public void setRota(List<Coordenadas> rota) { this.rota = rota; }
    public Coordenadas getPosicao() { return posicao; }
    public void setPosicao(Coordenadas posicao) { this.posicao = posicao; }
    public Integer getAlunos() { return alunos; }
    public void setAlunos(Integer alunos) { this.alunos = alunos; }
    public Integer getOcorrencias() { return ocorrencias; }
    public void setOcorrencias(Integer ocorrencias) { this.ocorrencias = ocorrencias; }
    public Long getUltimaAtualizacao() { return ultimaAtualizacao; }
    public void setUltimaAtualizacao(Long ultimaAtualizacao) { this.ultimaAtualizacao = ultimaAtualizacao; }
    public String getNumRota() { return numRota; }
    public void setNumRota(String numRota) { this.numRota = numRota; }
    public Integer getLotacao() { return lotacao; }
    public void setLotacao(Integer lotacao) { this.lotacao = lotacao; }
    public Integer getAlunosABordo() { return alunosABordo; }
    public void setAlunosABordo(Integer alunosABordo) { this.alunosABordo = alunosABordo; }
    public Integer getFaltantes() { return faltantes; }
    public void setFaltantes(Integer faltantes) { this.faltantes = faltantes; }
    public String getProximaParada() { return proximaParada; }
    public void setProximaParada(String proximaParada) { this.proximaParada = proximaParada; }
    public String getPrevisaoProximaParada() { return previsaoProximaParada; }
    public void setPrevisaoProximaParada(String previsaoProximaParada) { this.previsaoProximaParada = previsaoProximaParada; }

    public static class Coordenadas {
        private String latitude;
        private String longitude;

        public Coordenadas() {}

        public Coordenadas(String latitude, String longitude) {
            this.latitude = latitude;
            this.longitude = longitude;
        }

        public static Coordenadas of(String latitude, String longitude) {
            return new Coordenadas(latitude, longitude);
        }

        public String getLatitude() { return latitude; }
        public void setLatitude(String latitude) { this.latitude = latitude; }
        public String getLongitude() { return longitude; }
        public void setLongitude(String longitude) { this.longitude = longitude; }
    }
}
