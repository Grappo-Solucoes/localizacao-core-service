package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.Position;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class RoutePredictionServiceTest {

    private RoutePredictionService service;

    @BeforeEach
    void setUp() {
        service = new RoutePredictionService();
    }

    @Test
    void retornaEmptyComMenosDe3Posicoes() {
        service.addPosition("v1", -23.5, -46.6, 1000);
        service.addPosition("v1", -23.501, -46.601, 2000);

        Optional<Position> prediction = service.predictNextPosition("v1");
        assertThat(prediction).isEmpty();
    }

    @Test
    void retornaEmptyParaEntidadeDesconhecida() {
        Optional<Position> prediction = service.predictNextPosition("desconhecida");
        assertThat(prediction).isEmpty();
    }

    @Test
    void preveProximaPosicaoCom3Posicoes() {
        service.addPosition("v2", -23.5000, -46.6000, 1000);
        service.addPosition("v2", -23.5010, -46.6010, 2000);
        service.addPosition("v2", -23.5020, -46.6020, 3000);

        Optional<Position> prediction = service.predictNextPosition("v2");

        assertThat(prediction).isPresent();
        Position predicted = prediction.get();
        // Previsão deve ser próxima de -23.5030, -46.6030
        assertThat(predicted.getPoint().latitude()).isBetween(-23.5035, -23.5025);
        assertThat(predicted.getPoint().longitude()).isBetween(-46.6035, -46.6025);
        assertThat(predicted.getTimestamp()).isBetween(3500L, 4500L);
    }

    @Test
    void mantemApenasUltimas10Posicoes() {
        for (int i = 0; i < 15; i++) {
            service.addPosition("v3", -23.5 + i * 0.001, -46.6 + i * 0.001, 1000 + i * 1000);
        }

        Optional<Position> prediction = service.predictNextPosition("v3");
        assertThat(prediction).isPresent();
    }

    @Test
    void prevePosicaoComMovimentoConstante() {
        service.addPosition("v4", -23.5000, -46.6000, 1000);
        service.addPosition("v4", -23.5010, -46.6010, 2000);
        service.addPosition("v4", -23.5020, -46.6020, 3000);

        Optional<Position> prediction = service.predictNextPosition("v4");

        assertThat(prediction).isPresent();
        Position predicted = prediction.get();
        assertThat(predicted.getPoint().latitude()).isBetween(-23.5035, -23.5025);
        assertThat(predicted.getPoint().longitude()).isBetween(-46.6035, -46.6025);
    }
}
