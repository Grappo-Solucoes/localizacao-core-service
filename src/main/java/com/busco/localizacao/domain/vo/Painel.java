package com.busco.localizacao.domain.vo;

/**
 * Painel resumido da Visão Geral.
 */
public class Painel {
    private Integer viagens;
    private Integer alunos;
    private Integer ocorrencias;

    public Painel() {}

    public Painel(Integer viagens, Integer alunos, Integer ocorrencias) {
        this.viagens = viagens;
        this.alunos = alunos;
        this.ocorrencias = ocorrencias;
    }

    public static Painel of(Integer viagens, Integer alunos, Integer ocorrencias) {
        return new Painel(viagens, alunos, ocorrencias);
    }

    public Integer getViagens() { return viagens; }
    public void setViagens(Integer viagens) { this.viagens = viagens; }
    public Integer getAlunos() { return alunos; }
    public void setAlunos(Integer alunos) { this.alunos = alunos; }
    public Integer getOcorrencias() { return ocorrencias; }
    public void setOcorrencias(Integer ocorrencias) { this.ocorrencias = ocorrencias; }
}
