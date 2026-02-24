package com.busco.localizacao.domain.entity;

import com.busco.localizacao.domain.vo.GeoPoint;

public class PontoEmbarque {
    private String alunoId;
    private String pontoId; // referência ao ponto da rota
    private GeoPoint localizacao;
    private TipoEmbarque tipo;
    private Long horarioPrevisto;
    private Long horarioRealizado;
    private boolean realizado;
    private StatusEmbarque status;
    private String observacao;

    public enum TipoEmbarque {
        EMBARQUE("Embarque"),
        DESEMBARQUE("Desembarque");

        private final String descricao;

        TipoEmbarque(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }
    }

    public enum StatusEmbarque {
        PENDENTE("Aguardando"),
        REALIZADO("Realizado"),
        CANCELADO("Cancelado"),
        NAO_REALIZADO("Não Realizado");

        private final String descricao;

        StatusEmbarque(String descricao) {
            this.descricao = descricao;
        }

        public String getDescricao() {
            return descricao;
        }
    }

    // Construtor completo
    public PontoEmbarque(String alunoId, String pontoId, double latitude, double longitude, TipoEmbarque tipo) {
        this.alunoId = alunoId;
        this.pontoId = pontoId;
        this.localizacao = new GeoPoint(latitude, longitude);
        this.tipo = tipo;
        this.realizado = false;
        this.status = StatusEmbarque.PENDENTE;
    }

    // Construtor com GeoPoint
    public PontoEmbarque(String alunoId, String pontoId, GeoPoint localizacao, TipoEmbarque tipo) {
        this.alunoId = alunoId;
        this.pontoId = pontoId;
        this.localizacao = localizacao;
        this.tipo = tipo;
        this.realizado = false;
        this.status = StatusEmbarque.PENDENTE;
    }

    // Getters e Setters
    public String getAlunoId() {
        return alunoId;
    }

    public void setAlunoId(String alunoId) {
        this.alunoId = alunoId;
    }

    public String getPontoId() {
        return pontoId;
    }

    public void setPontoId(String pontoId) {
        this.pontoId = pontoId;
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

    public TipoEmbarque getTipo() {
        return tipo;
    }

    public void setTipo(TipoEmbarque tipo) {
        this.tipo = tipo;
    }

    public Long getHorarioPrevisto() {
        return horarioPrevisto;
    }

    public void setHorarioPrevisto(Long horarioPrevisto) {
        this.horarioPrevisto = horarioPrevisto;
    }

    public Long getHorarioRealizado() {
        return horarioRealizado;
    }

    public void setHorarioRealizado(Long horarioRealizado) {
        this.horarioRealizado = horarioRealizado;
        if (horarioRealizado != null) {
            this.realizado = true;
            this.status = StatusEmbarque.REALIZADO;
        }
    }

    public boolean isRealizado() {
        return realizado;
    }

    public void setRealizado(boolean realizado) {
        this.realizado = realizado;
        if (realizado) {
            this.status = StatusEmbarque.REALIZADO;
            if (this.horarioRealizado == null) {
                this.horarioRealizado = System.currentTimeMillis();
            }
        }
    }

    public StatusEmbarque getStatus() {
        return status;
    }

    public void setStatus(StatusEmbarque status) {
        this.status = status;
        if (status == StatusEmbarque.REALIZADO) {
            this.realizado = true;
            if (this.horarioRealizado == null) {
                this.horarioRealizado = System.currentTimeMillis();
            }
        }
    }

    public String getObservacao() {
        return observacao;
    }

    public void setObservacao(String observacao) {
        this.observacao = observacao;
    }

    // Métodos úteis
    public boolean isAtrasado() {
        if (horarioPrevisto != null && horarioRealizado != null) {
            return horarioRealizado > horarioPrevisto + 300000; // 5 minutos de tolerância
        }
        return false;
    }

    public long getTempoAtraso() {
        if (horarioPrevisto != null && horarioRealizado != null && horarioRealizado > horarioPrevisto) {
            return horarioRealizado - horarioPrevisto;
        }
        return 0;
    }

    public boolean isEmbarque() {
        return tipo == TipoEmbarque.EMBARQUE;
    }

    public boolean isDesembarque() {
        return tipo == TipoEmbarque.DESEMBARQUE;
    }

    public void cancelar(String motivo) {
        this.status = StatusEmbarque.CANCELADO;
        this.observacao = motivo;
        this.realizado = false;
    }

    public void naoRealizado(String motivo) {
        this.status = StatusEmbarque.NAO_REALIZADO;
        this.observacao = motivo;
        this.realizado = false;
    }

    @Override
    public String toString() {
        return String.format("PontoEmbarque{alunoId='%s', pontoId='%s', tipo=%s, status=%s, realizado=%s}",
                alunoId, pontoId, tipo, status, realizado);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PontoEmbarque that = (PontoEmbarque) o;
        return alunoId.equals(that.alunoId) && pontoId.equals(that.pontoId) && tipo == that.tipo;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(alunoId, pontoId, tipo);
    }
}