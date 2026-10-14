package com.busco.localizacao.domain.services;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SpeedCalculatorTest {

    private final SpeedCalculator calculator = new SpeedCalculator();

    @Test
    void calculaVelocidadeCorretaParaDistanciaConhecida() {
        // 1 km em 1 hora = 1 km/h
        // Usando coordenadas aproximadas: 1 grau de latitude ≈ 111 km
        double lat1 = -23.5505;
        double lng1 = -46.6333;
        double lat2 = -23.5595; // ~1 km ao sul
        double lng2 = -46.6333;
        long timeMs = 3600_000; // 1 hora

        double speed = calculator.calculate(lat1, lng1, lat2, lng2, timeMs);

        assertThat(speed).isBetween(0.9, 1.1); // ~1 km/h
    }

    @Test
    void retornaZeroQuandoTempoZero() {
        double speed = calculator.calculate(-23.5, -46.6, -23.6, -46.7, 0);
        assertThat(speed).isZero();
    }

    @Test
    void calculaVelocidadeAltaParaMovimentoRapido() {
        // 100 km em 1 hora = 100 km/h
        double lat1 = -23.5505;
        double lng1 = -46.6333;
        double lat2 = -22.6505; // ~100 km ao norte
        double lng2 = -46.6333;
        long timeMs = 3600_000; // 1 hora

        double speed = calculator.calculate(lat1, lng1, lat2, lng2, timeMs);

        assertThat(speed).isBetween(95.0, 105.0); // ~100 km/h
    }

    @Test
    void retornaZeroParaMesmaPosicao() {
        double speed = calculator.calculate(-23.5, -46.6, -23.5, -46.6, 1000);
        assertThat(speed).isZero();
    }

    @Test
    void calculaVelocidadeUrbanaRazoavel() {
        // ~500m em 30s = ~60 km/h
        // 0.0045 graus de latitude ≈ 500m
        double lat1 = -23.5505;
        double lng1 = -46.6333;
        double lat2 = -23.5550; // ~500m ao sul
        double lng2 = -46.6333;
        long timeMs = 30_000; // 30 segundos

        double speed = calculator.calculate(lat1, lng1, lat2, lng2, timeMs);

        assertThat(speed).isBetween(50.0, 70.0); // ~60 km/h
    }

    @Test
    void calculaVelocidadeParaCenarioTipico() {
        // ~1km em 60s = ~60 km/h
        // 0.009 graus de latitude ≈ 1km
        double lat1 = -23.5505;
        double lng1 = -46.6333;
        double lat2 = -23.5595; // ~1km ao sul
        double lng2 = -46.6333;
        long timeMs = 60_000; // 60 segundos

        double speed = calculator.calculate(lat1, lng1, lat2, lng2, timeMs);

        assertThat(speed).isBetween(50.0, 70.0); // ~60 km/h
    }
}
