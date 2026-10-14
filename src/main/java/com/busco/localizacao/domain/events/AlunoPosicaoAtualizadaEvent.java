package com.busco.localizacao.domain.events;

import com.busco.localizacao.sk.ddd.DomainEvent;

import java.time.Instant;
import java.util.Objects;

/**
 * Evento de posição atualizada do aluno.
 * Imutável: todos os campos são final e definidos no construtor.
 */
public final class AlunoPosicaoAtualizadaEvent implements DomainEvent {

    private final String alunoId;
    private final String viagemId;
    private final double latitude;
    private final double longitude;
    private final long timestamp;
    private final Double distanciaDoOnibus;
    private final Long etaParaEmbarque;
    private final Boolean aptoParaEmbarque;
    private final String pontoEmbarqueId;
    private final String pontoEmbarqueNome;
    private final String tipoEmbarque;
    private final Double distanciaAtePonto;
    private final Long horarioPrevisto;
    private final Boolean atrasado;
    private final String status;
    private final Double velocidadeOnibus;
    private final Integer passageirosABordo;
    private final Double precisaoGPS;
    private final Instant occurredOn;

    private AlunoPosicaoAtualizadaEvent(Builder builder) {
        this.alunoId = builder.alunoId;
        this.viagemId = builder.viagemId;
        this.latitude = builder.latitude;
        this.longitude = builder.longitude;
        this.timestamp = builder.timestamp;
        this.distanciaDoOnibus = builder.distanciaDoOnibus;
        this.etaParaEmbarque = builder.etaParaEmbarque;
        this.aptoParaEmbarque = builder.aptoParaEmbarque;
        this.pontoEmbarqueId = builder.pontoEmbarqueId;
        this.pontoEmbarqueNome = builder.pontoEmbarqueNome;
        this.tipoEmbarque = builder.tipoEmbarque;
        this.distanciaAtePonto = builder.distanciaAtePonto;
        this.horarioPrevisto = builder.horarioPrevisto;
        this.atrasado = builder.atrasado;
        this.status = builder.status;
        this.velocidadeOnibus = builder.velocidadeOnibus;
        this.passageirosABordo = builder.passageirosABordo;
        this.precisaoGPS = builder.precisaoGPS;
        this.occurredOn = Instant.now();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getAlunoId() { return alunoId; }
    public String getViagemId() { return viagemId; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public long getTimestamp() { return timestamp; }
    public Double getDistanciaDoOnibus() { return distanciaDoOnibus; }
    public Long getEtaParaEmbarque() { return etaParaEmbarque; }
    public Boolean getAptoParaEmbarque() { return aptoParaEmbarque; }
    public String getPontoEmbarqueId() { return pontoEmbarqueId; }
    public String getPontoEmbarqueNome() { return pontoEmbarqueNome; }
    public String getTipoEmbarque() { return tipoEmbarque; }
    public Double getDistanciaAtePonto() { return distanciaAtePonto; }
    public Long getHorarioPrevisto() { return horarioPrevisto; }
    public Boolean getAtrasado() { return atrasado; }
    public String getStatus() { return status; }
    public Double getVelocidadeOnibus() { return velocidadeOnibus; }
    public Integer getPassageirosABordo() { return passageirosABordo; }
    public Double getPrecisaoGPS() { return precisaoGPS; }

    @Override
    public Instant getOccurredOn() { return occurredOn; }

    public boolean deveNotificarMotorista() {
        return "NO_PONTO".equals(status) || "ONIBUS_PROXIMO".equals(status);
    }

    public boolean deveNotificarAluno() {
        return "ONIBUS_PROXIMO".equals(status) ||
                (aptoParaEmbarque != null && aptoParaEmbarque && distanciaDoOnibus != null && distanciaDoOnibus < 0.5);
    }

    public String getEtaFormatado() {
        if (etaParaEmbarque == null || etaParaEmbarque <= 0) {
            return "Indisponível";
        }
        long minutos = etaParaEmbarque / 60;
        long segundos = etaParaEmbarque % 60;
        if (minutos > 0) {
            return String.format("%d min %d seg", minutos, segundos);
        } else {
            return String.format("%d seg", segundos);
        }
    }

    public String getDistanciaFormatada() {
        if (distanciaDoOnibus == null) {
            return "Indisponível";
        }
        if (distanciaDoOnibus < 1) {
            return String.format("%.0f m", distanciaDoOnibus * 1000);
        } else {
            return String.format("%.1f km", distanciaDoOnibus);
        }
    }

    @Override
    public String toString() {
        return String.format(
                "AlunoPosicaoAtualizadaEvent{alunoId='%s', viagemId='%s', lat=%.6f, lng=%.6f, status=%s, apto=%s, distanciaOnibus=%.2fkm, eta=%s, ponto='%s', distanciaPonto=%.1fm}",
                alunoId, viagemId, latitude, longitude, status,
                aptoParaEmbarque,
                distanciaDoOnibus != null ? distanciaDoOnibus : 0,
                getEtaFormatado(),
                pontoEmbarqueNome != null ? pontoEmbarqueNome : pontoEmbarqueId,
                distanciaAtePonto != null ? distanciaAtePonto : 0
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AlunoPosicaoAtualizadaEvent that = (AlunoPosicaoAtualizadaEvent) o;
        return Objects.equals(alunoId, that.alunoId) && timestamp == that.timestamp;
    }

    @Override
    public int hashCode() {
        return Objects.hash(alunoId, timestamp);
    }

    public static class Builder {
        private String alunoId;
        private String viagemId;
        private double latitude;
        private double longitude;
        private long timestamp;
        private Double distanciaDoOnibus;
        private Long etaParaEmbarque;
        private Boolean aptoParaEmbarque;
        private String pontoEmbarqueId;
        private String pontoEmbarqueNome;
        private String tipoEmbarque;
        private Double distanciaAtePonto;
        private Long horarioPrevisto;
        private Boolean atrasado;
        private String status;
        private Double velocidadeOnibus;
        private Integer passageirosABordo;
        private Double precisaoGPS;

        public Builder alunoId(String alunoId) { this.alunoId = alunoId; return this; }
        public Builder viagemId(String viagemId) { this.viagemId = viagemId; return this; }
        public Builder latitude(double latitude) { this.latitude = latitude; return this; }
        public Builder longitude(double longitude) { this.longitude = longitude; return this; }
        public Builder timestamp(long timestamp) { this.timestamp = timestamp; return this; }
        public Builder distanciaDoOnibus(Double distanciaDoOnibus) { this.distanciaDoOnibus = distanciaDoOnibus; return this; }
        public Builder etaParaEmbarque(Long etaParaEmbarque) { this.etaParaEmbarque = etaParaEmbarque; return this; }
        public Builder aptoParaEmbarque(Boolean aptoParaEmbarque) { this.aptoParaEmbarque = aptoParaEmbarque; return this; }
        public Builder pontoEmbarqueId(String pontoEmbarqueId) { this.pontoEmbarqueId = pontoEmbarqueId; return this; }
        public Builder pontoEmbarqueNome(String pontoEmbarqueNome) { this.pontoEmbarqueNome = pontoEmbarqueNome; return this; }
        public Builder tipoEmbarque(String tipoEmbarque) { this.tipoEmbarque = tipoEmbarque; return this; }
        public Builder distanciaAtePonto(Double distanciaAtePonto) { this.distanciaAtePonto = distanciaAtePonto; return this; }
        public Builder horarioPrevisto(Long horarioPrevisto) { this.horarioPrevisto = horarioPrevisto; return this; }
        public Builder atrasado(Boolean atrasado) { this.atrasado = atrasado; return this; }
        public Builder status(String status) { this.status = status; return this; }
        public Builder velocidadeOnibus(Double velocidadeOnibus) { this.velocidadeOnibus = velocidadeOnibus; return this; }
        public Builder passageirosABordo(Integer passageirosABordo) { this.passageirosABordo = passageirosABordo; return this; }
        public Builder precisaoGPS(Double precisaoGPS) { this.precisaoGPS = precisaoGPS; return this; }

        public AlunoPosicaoAtualizadaEvent build() {
            return new AlunoPosicaoAtualizadaEvent(this);
        }
    }
}
