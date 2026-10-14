package com.busco.localizacao.domain.services;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.net.ConnectException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Gate da spec localizacao/enriquecimento para geocodificação reversa:
 * endereço obtido vai no evento; falha, silêncio ou resposta inútil resultam em
 * ausência de endereço (nunca um texto sentinela apresentado como endereço).
 */
class GeocodingServiceTest {

    private static final Duration TIMEOUT_CURTO = Duration.ofMillis(200);
    private static final Duration LIMITE_ESPERA_NO_TESTE = Duration.ofSeconds(5);

    private GeocodingService servicoQueResponde(Mono<ClientResponse> resposta) {
        WebClient webClient = WebClient.builder()
                .baseUrl("https://geocoding.invalido")
                .exchangeFunction(request -> resposta)
                .build();
        return new GeocodingService(webClient, TIMEOUT_CURTO);
    }

    private static ClientResponse respostaJson(String corpo) {
        return ClientResponse.create(HttpStatus.OK)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(corpo)
                .build();
    }

    @Test
    void enderecoObtido() {
        GeocodingService servico = servicoQueResponde(
                Mono.just(respostaJson("{\"display_name\":\"Rua Teste, 100\"}")));

        String endereco = servico.reverseGeocode(-23.5505, -46.6333)
                .block(LIMITE_ESPERA_NO_TESTE);

        assertEquals("Rua Teste, 100", endereco);
    }

    @Test
    void respostaRealDoNominatim_extraiDisplayName() {
        // Payload com o formato real do Nominatim: a chave e "display_name" (snake_case)
        // e vem acompanhada de varios campos extras (address, boundingbox, ...).
        String payload = "{"
                + "\"place_id\":123,\"licence\":\"ODbL\",\"osm_type\":\"way\","
                + "\"lat\":\"-23.5505\",\"lon\":\"-46.6333\","
                + "\"display_name\":\"Rua Teste, 100, Sao Paulo, Brasil\","
                + "\"address\":{\"road\":\"Rua Teste\",\"house_number\":\"100\","
                + "\"city\":\"Sao Paulo\",\"state\":\"SP\",\"country_code\":\"br\"},"
                + "\"boundingbox\":[\"-23.56\",\"-23.54\",\"-46.64\",\"-46.62\"]}";

        GeocodingService servico = servicoQueResponde(Mono.just(respostaJson(payload)));

        String endereco = servico.reverseGeocode(-23.5505, -46.6333)
                .block(LIMITE_ESPERA_NO_TESTE);

        assertEquals("Rua Teste, 100, Sao Paulo, Brasil", endereco);
    }

    @Test
    void provedorComErroDeConexao_publicaSemEndereco() {
        GeocodingService servico = servicoQueResponde(
                Mono.error(new ConnectException("conexao recusada")));

        assertNull(servico.reverseGeocode(-23.5505, -46.6333).block(LIMITE_ESPERA_NO_TESTE));
    }

    @Test
    void provedorNaoResponde_timeoutPublicaSemEndereco() {
        GeocodingService servico = servicoQueResponde(Mono.never());

        long inicio = System.nanoTime();
        String endereco = servico.reverseGeocode(-23.5505, -46.6333)
                .block(LIMITE_ESPERA_NO_TESTE);
        long decorridoMs = (System.nanoTime() - inicio) / 1_000_000;

        assertNull(endereco);
        // O ponto do teste: a espera é limitada pelo timeout, não indefinida.
        org.junit.jupiter.api.Assertions.assertTrue(
                decorridoMs < 2_000, "espera deveria ser limitada, levou " + decorridoMs + "ms");
    }

    @Test
    void respostaHttpComErro_publicaSemEndereco() {
        GeocodingService servico = servicoQueResponde(Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR).body("falha").build()));

        assertNull(servico.reverseGeocode(-23.5505, -46.6333).block(LIMITE_ESPERA_NO_TESTE));
    }

    @Test
    void respostaSemEnderecoUtilizavel_publicaSemEndereco() {
        GeocodingService semNome = servicoQueResponde(Mono.just(respostaJson("{}")));
        assertNull(semNome.reverseGeocode(-23.5505, -46.6333).block(LIMITE_ESPERA_NO_TESTE));

        GeocodingService emBranco = servicoQueResponde(
                Mono.just(respostaJson("{\"display_name\":\"   \"}")));
        assertNull(emBranco.reverseGeocode(-23.5505, -46.6333).block(LIMITE_ESPERA_NO_TESTE));
    }

    @Test
    void nuncaDevolveTextoSentinelaNoLugarDoEndereco() {
        // Antes: onErrorReturn("Endereço não disponível") fazia o evento carregar um
        // endereço falso. A spec exige evento SEM endereço.
        GeocodingService servico = servicoQueResponde(
                Mono.error(new IllegalStateException("provedor fora do ar")));

        String resultado = servico.reverseGeocode(-23.5505, -46.6333)
                .block(LIMITE_ESPERA_NO_TESTE);

        assertNull(resultado, "falha de geocodificacao nao pode virar endereco");
    }
}
