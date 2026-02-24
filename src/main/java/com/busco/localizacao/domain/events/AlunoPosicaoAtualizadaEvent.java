package com.busco.localizacao.domain.events;

import com.busco.localizacao.domain.entity.PontoEmbarque;

public class AlunoPosicaoAtualizadaEvent {
    public String alunoId;
    public String viagemId;
    public double latitude;
    public double longitude;
    public long timestamp;
    public Double distanciaDoOnibus; // distância até o ônibus da viagem em km
    public Long etaParaEmbarque; // tempo estimado para o ônibus chegar em segundos
    public Boolean aptoParaEmbarque; // true se está no ponto correto
    public String pontoEmbarqueId; // ID do ponto de embarque na rota
    public String pontoEmbarqueNome; // Nome do ponto (ex: "Escola Estadual")
    public String tipoEmbarque; // "EMBARQUE" ou "DESEMBARQUE"
    public Double distanciaAtePonto; // distância até o ponto de embarque em metros
    public Long horarioPrevisto; // horário previsto para embarque/desembarque
    public Boolean atrasado; // true se está atrasado em relação ao horário previsto
    public String status; // "AGUARDANDO", "NO_PONTO", "FORA_DO_PONTO", "ONIBUS_PROXIMO", "EMBARCADO", "DESEMBARCADO", "SEM_PONTO_DEFINIDO"
    public Double velocidadeOnibus; // velocidade atual do ônibus em km/h
    public Integer passageirosABordo; // número de passageiros (se disponível)
    public Double precisaoGPS; // precisão da localização em metros

    // Método para definir status baseado na situação
    public void definirStatusPorSituacao(double distanciaOnibusMeters, double distanciaPontoMeters, boolean pontoCorreto) {
        if (!pontoCorreto) {
            this.status = "FORA_DO_PONTO";
            return;
        }

        if (distanciaPontoMeters > 100) {
            this.status = "FORA_DO_PONTO";
        } else if (distanciaPontoMeters <= 100 && distanciaPontoMeters > 20) {
            this.status = "NO_PONTO";
        } else if (distanciaPontoMeters <= 20) {
            if (distanciaOnibusMeters < 200) {
                this.status = "ONIBUS_PROXIMO";
            } else {
                this.status = "AGUARDANDO";
            }
        }
    }

    // Método para verificar se deve notificar o motorista
    public boolean deveNotificarMotorista() {
        return "NO_PONTO".equals(status) || "ONIBUS_PROXIMO".equals(status);
    }

    // Método para verificar se deve notificar o aluno
    public boolean deveNotificarAluno() {
        return "ONIBUS_PROXIMO".equals(status) ||
                (aptoParaEmbarque != null && aptoParaEmbarque && distanciaDoOnibus != null && distanciaDoOnibus < 0.5);
    }

    // Método para calcular se está apto para embarque
    public void calcularAptidaoEmbarque(double distanciaPontoMeters, double distanciaOnibusMeters) {
        this.aptoParaEmbarque = distanciaPontoMeters <= 100 && distanciaOnibusMeters <= 500;
    }

    // Método para calcular ETA formatado
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

    // Método para calcular distância formatada
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
                "AlunoPosicaoAtualizadaEvent{" +
                        "alunoId='%s', viagemId='%s', lat=%.6f, lng=%.6f, status=%s, " +
                        "apto=%s, distanciaOnibus=%.2fkm, eta=%s, ponto='%s', distanciaPonto=%.1fm}",
                alunoId, viagemId, latitude, longitude, status,
                aptoParaEmbarque,
                distanciaDoOnibus != null ? distanciaDoOnibus : 0,
                getEtaFormatado(),
                pontoEmbarqueNome != null ? pontoEmbarqueNome : pontoEmbarqueId,
                distanciaAtePonto != null ? distanciaAtePonto : 0
        );
    }

    public String getAlunoId() {
        return alunoId;
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

    public Double getDistanciaDoOnibus() {
        return distanciaDoOnibus;
    }

    public Long getEtaParaEmbarque() {
        return etaParaEmbarque;
    }

    public Boolean getAptoParaEmbarque() {
        return aptoParaEmbarque;
    }

    public String getPontoEmbarqueId() {
        return pontoEmbarqueId;
    }

    public String getPontoEmbarqueNome() {
        return pontoEmbarqueNome;
    }

    public String getTipoEmbarque() {
        return tipoEmbarque;
    }

    public Double getDistanciaAtePonto() {
        return distanciaAtePonto;
    }

    public Long getHorarioPrevisto() {
        return horarioPrevisto;
    }

    public Boolean getAtrasado() {
        return atrasado;
    }

    public String getStatus() {
        return status;
    }

    public Double getVelocidadeOnibus() {
        return velocidadeOnibus;
    }

    public Integer getPassageirosABordo() {
        return passageirosABordo;
    }

    public Double getPrecisaoGPS() {
        return precisaoGPS;
    }
}