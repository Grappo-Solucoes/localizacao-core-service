package com.busco.localizacao.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contrato de entrada do topico {@code tracking.posicao-aluno.v1} (v1), publicado pelo
 * {@code tracking-ingestion-service}. Chave da mensagem: {@code alunoId}.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PosicaoAlunoEvento(String eventId,
                                 String viagemId,
                                 String alunoId,
                                 double latitude,
                                 double longitude,
                                 long timestamp,
                                 String occurredOn) {
}
