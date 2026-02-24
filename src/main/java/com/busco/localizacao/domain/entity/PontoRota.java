package com.busco.localizacao.domain.entity;

import com.busco.localizacao.domain.vo.GeoPoint;

public class PontoRota {
    private String id;
    private String nome;
    private GeoPoint localizacao;
    private TipoPonto tipo;
    private boolean visitado;
    private Long horarioPrevisto; // timestamp previsto de chegada
    private Long horarioChegada; // timestamp real de chegada
    private Integer tempoParada; // tempo previsto de parada em segundos
    private Integer ordem; // ordem na rota

    public enum TipoPonto {
        TERMINAL("Terminal"),
        ESCOLA("Escola"),
        PARADA("Ponto de Parada");

        private final String descricao;

        TipoPonto(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }
    }

    // Construtor completo
    public PontoRota(String id, String nome, double latitude, double longitude, TipoPonto tipo) {
        this.id = id;
        this.nome = nome;
        this.localizacao = new GeoPoint(latitude, longitude);
        this.tipo = tipo;
        this.visitado = false;
    }

    // Construtor com GeoPoint
    public PontoRota(String id, String nome, GeoPoint localizacao, TipoPonto tipo) {
        this.id = id;
        this.nome = nome;
        this.localizacao = localizacao;
        this.tipo = tipo;
        this.visitado = false;
    }

    // Getters e Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public double getLatitude() {
        return localizacao != null ? localizacao.latitude() : 0;
    }

    public double getLongitude() {
        return localizacao != null ? localizacao.longitude() : 0;
    }

    public GeoPoint getLocalizacao() {
        return localizacao;
    }

    public void setLocalizacao(GeoPoint localizacao) {
        this.localizacao = localizacao;
    }

    public TipoPonto getTipo() {
        return tipo;
    }

    public void setTipo(TipoPonto tipo) {
        this.tipo = tipo;
    }

    public boolean isVisitado() {
        return visitado;
    }

    public void setVisitado(boolean visitado) {
        this.visitado = visitado;
    }

    public Long getHorarioPrevisto() {
        return horarioPrevisto;
    }

    public void setHorarioPrevisto(Long horarioPrevisto) {
        this.horarioPrevisto = horarioPrevisto;
    }

    public Long getHorarioChegada() {
        return horarioChegada;
    }

    public void setHorarioChegada(Long horarioChegada) {
        this.horarioChegada = horarioChegada;
        if (horarioChegada != null) {
            this.visitado = true;
        }
    }

    public Integer getTempoParada() {
        return tempoParada;
    }

    public void setTempoParada(Integer tempoParada) {
        this.tempoParada = tempoParada;
    }

    public Integer getOrdem() {
        return ordem;
    }

    public void setOrdem(Integer ordem) {
        this.ordem = ordem;
    }

    // Métodos úteis
    public boolean isAtrasado() {
        if (horarioPrevisto != null && horarioChegada != null) {
            return horarioChegada > horarioPrevisto + 300000; // 5 minutos de tolerância
        }
        return false;
    }

    public long getTempoAtraso() {
        if (horarioPrevisto != null && horarioChegada != null && horarioChegada > horarioPrevisto) {
            return horarioChegada - horarioPrevisto;
        }
        return 0;
    }

    @Override
    public String toString() {
        return String.format("PontoRota{id='%s', nome='%s', tipo=%s, visitado=%s, lat=%.6f, lng=%.6f}",
                id, nome, tipo, visitado, getLatitude(), getLongitude());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PontoRota pontoRota = (PontoRota) o;
        return id.equals(pontoRota.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}