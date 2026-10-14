package com.busco.localizacao.infra.kafka;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Contrato de entrada do topico {@code tracking.posicao-veiculo.v1} (v1), publicado pelo
 * {@code tracking-ingestion-service}.
 *
 * <p>{@code timestamp} e o relogio do aparelho, em epoch millis, e e o valor usado no processamento
 * (consistencia, velocidade e o timestamp do evento publicado). {@code occurredOn} e o instante de
 * recebimento no ingestor: fica como texto porque e diagnostico e nao alimenta o dominio, evitando
 * acoplar o consumidor ao formato de data escolhido pelo produtor. Campos desconhecidos sao
 * ignorados: o produtor pode acrescentar campos sem quebrar este consumidor.</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PosicaoVeiculoEvento(String eventId,
                                   String viagemId,
                                   double latitude,
                                   double longitude,
                                   long timestamp,
                                   Double bateria,
                                   Double precisao,
                                   String provedor,
                                   String occurredOn) {
}
