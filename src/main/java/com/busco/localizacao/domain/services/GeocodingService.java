package com.busco.localizacao.domain.services;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * Geocodificação reversa (coordenada -> endereço).
 *
 * <p>Contrato (spec localizacao/enriquecimento, cenários "Endereço obtido" e
 * "Geocodificação indisponível"): quando o provedor responde, o endereço vai no evento;
 * quando falha ou não responde, o evento é publicado <b>sem endereço</b>, sem interromper
 * o pipeline. Por isso a falha vira {@code Mono.empty()} (nunca um texto sentinela como
 * "Endereço não disponível", que seria um endereço falso para quem consome) e existe
 * timeout, senão "não responde" travaria a publicação da posição indefinidamente.
 */
@Service
public class GeocodingService {

    private static final Logger log = LoggerFactory.getLogger(GeocodingService.class);

    private static final String BASE_URL = "https://nominatim.openstreetmap.org";

    /**
     * Limite de espera pela resposta do provedor. O endereço é opcional no evento, então
     * o pipeline prefere perder o endereço a segurar a posição: o timeout limita a espera
     * por posição mesmo quando o provedor está lento ou indisponível.
     */
    private static final Duration TIMEOUT_PADRAO = Duration.ofSeconds(3);

    private final WebClient webClient;
    private final Duration timeout;

    public GeocodingService() {
        this(
                WebClient.builder()
                        .baseUrl(BASE_URL)
                        .defaultHeader("User-Agent", "BuscoLocalizacao/1.0")
                        .build(),
                TIMEOUT_PADRAO
        );
    }

    GeocodingService(WebClient webClient, Duration timeout) {
        this.webClient = webClient;
        this.timeout = timeout;
    }

    /**
     * @return endereço obtido, ou {@code Mono.empty()} quando o provedor falha, não
     * responde dentro do timeout, ou responde sem endereço utilizável.
     */
    public Mono<String> reverseGeocode(double lat, double lng) {
        return webClient.get()
                .uri(uriBuilder -> uriBuilder
                        .path("/reverse")
                        .queryParam("lat", lat)
                        .queryParam("lon", lng)
                        .queryParam("format", "json")
                        .queryParam("addressdetails", "1")
                        .build())
                .retrieve()
                .bodyToMono(GeocodingResponse.class)
                .flatMap(response -> Mono.justOrEmpty(response.getDisplayName()))
                .filter(endereco -> !endereco.isBlank())
                .timeout(timeout)
                .onErrorResume(e -> {
                    log.warn("Geocodificacao indisponivel para ({}, {}): {}. "
                            + "Evento segue sem endereco.", lat, lng, e.toString());
                    return Mono.empty();
                });
    }

    private static class GeocodingResponse {

        // O Nominatim responde em snake_case ("display_name"); sem este mapeamento o
        // endereco decodificava como null em toda resposta bem-sucedida, deixando o
        // evento sem endereco mesmo com o provedor disponivel.
        @JsonProperty("display_name")
        private String displayName;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}
