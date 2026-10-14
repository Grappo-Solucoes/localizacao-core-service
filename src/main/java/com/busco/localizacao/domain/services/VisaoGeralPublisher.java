package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.vo.VisaoGeral;

/**
 * Porta de saída do domínio para a Visão ao Vivo.
 *
 * <p>Espelha o tratamento dado a {@link TrackingEventPublisher}: o domínio declara o
 * contrato e a tecnologia (STOMP sobre WebSocket, template de mensageria) fica em
 * {@code infra/ws}, conforme D2 do design do change
 * {@code substituicao-gradual-localizacao-core-service} ("o domínio não deve conhecer"
 * a tecnologia de mensageria; "essa direção está correta e será preservada").</p>
 */
public interface VisaoGeralPublisher {

    void send(VisaoGeral visaoGeral);
}
