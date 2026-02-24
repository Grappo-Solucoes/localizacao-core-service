package com.busco.localizacao.domain.services;

import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
public class GeocodingService {

    private final WebClient webClient;

    public GeocodingService() {
        this.webClient = WebClient.builder()
                .baseUrl("https://nominatim.openstreetmap.org")
                .defaultHeader("User-Agent", "BuscoLocalizacao/1.0")
                .build();
    }

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
                .map(response -> response.getDisplayName())
                .onErrorReturn("Endereço não disponível");
    }

    private static class GeocodingResponse {
        private String displayName;

        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
    }
}