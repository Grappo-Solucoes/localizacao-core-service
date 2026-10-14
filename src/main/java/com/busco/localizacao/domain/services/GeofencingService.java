package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.GeofenceEvent;
import com.busco.localizacao.domain.entity.PontoEmbarque;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class GeofencingService {

    private final Map<String, GeofenceZone> zones = new ConcurrentHashMap<>();

    /**
     * Id da zona de um ponto da rota. Fonte unica do esquema: quem registra e quem
     * consulta precisam concordar exatamente, senao a consulta nunca encontra a zona.
     */
    public static String zoneIdPontoRota(String viagemId, String pontoId) {
        return "ponto:" + viagemId + ":" + pontoId;
    }

    /**
     * Id da zona de um ponto de embarque de aluno.
     */
    public static String zoneIdPontoEmbarque(
            String viagemId, String alunoId, PontoEmbarque.TipoEmbarque tipo) {
        return "aluno:" + viagemId + ":" + alunoId + ":" + tipo;
    }

    public void registerZone(String zoneId, double centerLat, double centerLng, double radiusMeters) {
        zones.put(zoneId, new GeofenceZone(centerLat, centerLng, radiusMeters));
    }

    public Mono<GeofenceEvent> checkPosition(
            String entityId,
            double lat,
            double lng,
            String zoneId
    ) {
        GeofenceZone zone = zones.get(zoneId);
        if (zone == null) {
            return Mono.empty();
        }

        double distance = calculateDistance(lat, lng, zone.centerLat, zone.centerLng);
        boolean isInside = distance <= zone.radiusMeters;

        return Mono.just(new GeofenceEvent(
                entityId,
                zoneId,
                isInside,
                distance,
                Instant.now().toEpochMilli()
        ));
    }

    private double calculateDistance(double lat1, double lon1, double lat2, double lon2) {
        // Similar ao cálculo de distância
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) *
                        Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return 6371000 * c; // distância em metros
    }

    private record GeofenceZone(double centerLat, double centerLng, double radiusMeters) {}
}