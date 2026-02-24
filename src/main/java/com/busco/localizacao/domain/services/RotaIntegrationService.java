package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.entity.RotaViagem;
import com.busco.localizacao.infra.redis.AlunoEmbarqueRedisRepository;
import com.busco.localizacao.infra.redis.RotaRedisRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class RotaIntegrationService {

    private final RotaRedisRepository rotaRedisRepository;
    private final AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepository;
    private final GeofencingService geofencingService;

    public RotaIntegrationService(
            RotaRedisRepository rotaRedisRepository,
            AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepository,
            GeofencingService geofencingService) {
        this.rotaRedisRepository = rotaRedisRepository;
        this.alunoEmbarqueRedisRepository = alunoEmbarqueRedisRepository;
        this.geofencingService = geofencingService;
    }

    /**
     * Carrega rota da viagem do Redis
     */
    public Mono<RotaViagem> carregarRota(String viagemId) {
        return rotaRedisRepository.buscarRota(viagemId);
    }

    /**
     * Carrega pontos da rota
     */
    public Mono<List<PontoRota>> carregarPontosRota(String viagemId) {
        return rotaRedisRepository.buscarPontosRota(viagemId);
    }

    /**
     * Carrega pontos de embarque de todos os alunos
     */
    public Mono<Map<String, PontoEmbarque>> carregarPontosEmbarque(String viagemId) {
        return alunoEmbarqueRedisRepository.buscarMapaEmbarques(viagemId);
    }

    /**
     * Encontra o próximo ponto não visitado da rota
     */
    public Mono<Optional<PontoRota>> encontrarProximoPonto(String viagemId, double latAtual, double lngAtual) {
        return carregarPontosRota(viagemId)
                .map(pontos -> {
                    if (pontos == null || pontos.isEmpty()) {
                        return Optional.<PontoRota>empty();
                    }

                    PontoRota maisProximo = null;
                    double menorDistancia = Double.MAX_VALUE;

                    for (PontoRota ponto : pontos) {
                        if (ponto.isVisitado()) continue;

                        double distancia = calcularDistancia(
                                latAtual, lngAtual,
                                ponto.getLatitude(), ponto.getLongitude()
                        );

                        // Se está muito próximo, marca como visitado
                        if (distancia < 100) { // 100 metros
                            ponto.setVisitado(true);
                            rotaRedisRepository.atualizarPonto(viagemId, ponto).subscribe();
                            continue;
                        }

                        if (distancia < menorDistancia) {
                            menorDistancia = distancia;
                            maisProximo = ponto;
                        }
                    }

                    return Optional.ofNullable(maisProximo);
                });
    }

    /**
     * Encontra alunos no ponto atual
     */
    public Flux<AlunoNoPontoInfo> encontrarAlunosNoPonto(
            String viagemId,
            String pontoId,
            double latPonto,
            double lngPonto) {

        return alunoEmbarqueRedisRepository.buscarTodosPontosEmbarque(viagemId)
                .filter(ponto -> ponto.getPontoId().equals(pontoId) && !ponto.isRealizado())
                .flatMap(ponto -> {
                    double distancia = calcularDistancia(
                            latPonto, lngPonto,
                            ponto.getLatitude(), ponto.getLongitude()
                    );

                    if (distancia < 100) { // 100 metros
                        return Mono.just(new AlunoNoPontoInfo(
                                ponto.getAlunoId(),
                                ponto.getTipo(),
                                distancia
                        ));
                    }
                    return Mono.empty();
                });
    }

    /**
     * Registra zonas de geofencing para todos os pontos da rota
     */
    public Mono<Void> registrarGeofencingRota(String viagemId) {
        return carregarPontosRota(viagemId)
                .flatMapMany(Flux::fromIterable)
                .flatMap(ponto -> {
                    String zoneId = "ponto:" + viagemId + ":" + ponto.getId();
                    geofencingService.registerZone(
                            zoneId,
                            ponto.getLatitude(),
                            ponto.getLongitude(),
                            100 // raio de 100 metros
                    );
                    return Mono.empty();
                })
                .then();
    }

    /**
     * Registra zonas de geofencing para pontos de embarque dos alunos
     */
    public Mono<Void> registrarGeofencingAlunos(String viagemId) {
        return alunoEmbarqueRedisRepository.buscarTodosPontosEmbarque(viagemId)
                .flatMap(ponto -> {
                    String zoneId = "aluno:" + viagemId + ":" + ponto.getAlunoId() + ":" + ponto.getTipo();
                    geofencingService.registerZone(
                            zoneId,
                            ponto.getLatitude(),
                            ponto.getLongitude(),
                            50 // raio de 50 metros para embarque
                    );
                    return Mono.empty();
                })
                .then();
    }

    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1)) *
                        Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return 6371000 * c; // metros
    }

    // Record para informação de aluno no ponto
    public record AlunoNoPontoInfo(
            String alunoId,
            PontoEmbarque.TipoEmbarque tipo,
            double distanciaMetros
    ) {}
}