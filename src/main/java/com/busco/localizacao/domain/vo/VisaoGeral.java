package com.busco.localizacao.domain.vo;

import java.util.List;
import java.util.Objects;

/**
 * Snapshot da Visão Geral para publicação STOMP.
 * Espelha o contrato do busco-api: `painel` + `detalhes`.
 */
public class VisaoGeral {
    private Painel painel;
    private List<Detalhes> detalhes;

    public VisaoGeral() {}

    public VisaoGeral(Painel painel, List<Detalhes> detalhes) {
        this.painel = painel;
        this.detalhes = detalhes;
    }

    public Painel getPainel() { return painel; }
    public void setPainel(Painel painel) { this.painel = painel; }
    public List<Detalhes> getDetalhes() { return detalhes; }
    public void setDetalhes(List<Detalhes> detalhes) { this.detalhes = detalhes; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        VisaoGeral that = (VisaoGeral) o;
        return Objects.equals(painel, that.painel) && Objects.equals(detalhes, that.detalhes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(painel, detalhes);
    }
}
