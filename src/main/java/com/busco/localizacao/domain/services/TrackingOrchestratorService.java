package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.*;
import com.busco.localizacao.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.events.ViagemPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.vo.GeoPoint;
import com.busco.localizacao.infra.redis.AlunoEmbarqueRedisRepository;
import com.busco.localizacao.infra.redis.RotaRedisRepository;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TrackingOrchestratorService {

    private final TrackingEngine trackingEngine;
    private final TrackingEventPublisher eventPublisher;
    private final GeocodingService geocodingService;
    private final RouteCalculator routeCalculator;
    private final GeofencingService geofencingService;
    private final StopDetectionService stopDetectionService;
    private final RoutePredictionService predictionService;
    private final BatteryOptimizationService batteryOptimizationService;
    private final TrackingAntiFraudService antiFraudService;
    private final TrackingConsistencyService consistencyService;
    private final RotaRedisRepository rotaRedisRepository;
    private final AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepository;

    public TrackingOrchestratorService(
            TrackingEngine trackingEngine,
            TrackingEventPublisher eventPublisher,
            GeocodingService geocodingService,
            RouteCalculator routeCalculator,
            GeofencingService geofencingService,
            StopDetectionService stopDetectionService,
            RoutePredictionService predictionService,
            BatteryOptimizationService batteryOptimizationService,
            TrackingAntiFraudService antiFraudService,
            TrackingConsistencyService consistencyService,
            RotaRedisRepository rotaRedisRepository,
            AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepository) {
        this.trackingEngine = trackingEngine;
        this.eventPublisher = eventPublisher;
        this.geocodingService = geocodingService;
        this.routeCalculator = routeCalculator;
        this.geofencingService = geofencingService;
        this.stopDetectionService = stopDetectionService;
        this.predictionService = predictionService;
        this.batteryOptimizationService = batteryOptimizationService;
        this.antiFraudService = antiFraudService;
        this.consistencyService = consistencyService;
        this.rotaRedisRepository = rotaRedisRepository;
        this.alunoEmbarqueRedisRepository = alunoEmbarqueRedisRepository;
    }

    /**
     * Processa posição do motorista com todos os serviços integrados
     */
    public Mono<Void> processMotorista(
            String viagemId,
            double lat,
            double lng,
            long timestamp,
            Double batteryLevel,
            Double accuracy,
            String provedor
    ) {
        String key = "viagem:" + viagemId;

        return trackingEngine.getLastPosition(key)
                .flatMap(optionalState -> {
                    if (optionalState.isEmpty()) {
                        return Mono.just(optionalState);
                    }

                    TrackingState previousTracking = optionalState.get();
                    Position current = new Position(new GeoPoint(lat, lng), 0, timestamp);
                    Position previousPosition = new Position(
                            new GeoPoint(previousTracking.getLastLat(), previousTracking.getLastLng()),
                            0,
                            previousTracking.getLastTimestamp()
                    );

                    if (!consistencyService.isValid(previousPosition, current)) {
                        return Mono.empty(); // evento inconsistente
                    }
                    return Mono.just(optionalState);
                })
                .then(trackingEngine.process(key, lat, lng, timestamp))
                .flatMap(result -> {
                    // Verifica anti-fraude
                    Position pos = new Position(new GeoPoint(lat, lng), result.speed(), timestamp);
                    if (!antiFraudService.isValid(pos)) {
                        return Mono.empty(); // posição suspeita
                    }

                    // Adiciona ao histórico de previsão
                    predictionService.addPosition(viagemId, result.latitude(), result.longitude(), timestamp);

                    // Otimização baseada em bateria
                    BatteryOptimizationService.TrackingConfig batteryConfig;
                    if (batteryLevel != null) {
                        batteryConfig = batteryOptimizationService.getConfigForEntity(viagemId, batteryLevel);
                    } else {
                        batteryConfig = null;
                    }

                    // Busca dados da rota e alunos do Redis
                    Mono<List<PontoRota>> pontosRotaMono = rotaRedisRepository.buscarPontosRota(viagemId);
                    Mono<Map<String, PontoEmbarque>> alunosMono = alunoEmbarqueRedisRepository.buscarMapaEmbarques(viagemId);

                    return Mono.zip(pontosRotaMono, alunosMono)
                            .flatMap(rotaAlunosTuple -> {
                                List<PontoRota> pontosRota = rotaAlunosTuple.getT1();
                                Map<String, PontoEmbarque> alunos = rotaAlunosTuple.getT2();

                                // Encontra o próximo ponto da rota
                                Optional<PontoRota> proximoPonto = encontrarProximoPonto(pontosRota, lat, lng);

                                // Prepara operações paralelas
                                Mono<String> enderecoMono = geocodingService.reverseGeocode(lat, lng);

                                Mono<RouteInfo> routeMono = proximoPonto.isPresent() ?
                                        routeCalculator.calculateETA(
                                                lat, lng,
                                                proximoPonto.get().getLatitude(),
                                                proximoPonto.get().getLongitude(),
                                                result.speed()
                                        ) : Mono.just(new RouteInfo(0, 0));

                                Mono<Optional<Position>> predictionMono =
                                        Mono.justOrEmpty(Optional.ofNullable(predictionService.predictNextPosition(viagemId)));

                                Mono<String> zonaMono = verificarZonasProximas(viagemId, lat, lng, pontosRota, alunos);

                                return Mono.zip(enderecoMono, routeMono, predictionMono, zonaMono)
                                        .flatMap(tuple -> {
                                            ViagemPosicaoAtualizadaEvent event = criarEventoMotorista(
                                                    viagemId, result, tuple, batteryConfig, accuracy, provedor,
                                                    proximoPonto.orElse(null)
                                            );

                                            // Detecta paradas
                                            StopInfo stop = stopDetectionService.checkStop(
                                                    viagemId, result.speed(), timestamp
                                            );

                                            if (stop != null) {
                                                event.status = stop.getStopType();
//                                                event.addMetadata("stopDuration", stop.getDurationMinutes());
                                            }

                                            // Verifica se chegou em algum ponto
                                            verificarChegadaEmPonto(viagemId, pontosRota, alunos, lat, lng, timestamp)
                                                    .subscribe(alunosNoPonto -> {
                                                        if (!alunosNoPonto.isEmpty()) {
//                                                            event.addMetadata("alunosNoPonto", alunosNoPonto);
                                                        }
                                                    });

                                            return eventPublisher.publish(event);
                                        });
                            });
                });
    }

    /**
     * Processa posição do aluno integrado com a viagem
     */
    public Mono<Void> processAluno(
            String alunoId,
            String viagemId,
            double lat,
            double lng,
            long timestamp
    ) {
        String keyAluno = "aluno:" + alunoId;
        String keyViagem = "viagem:" + viagemId;

        return Mono.zip(
                trackingEngine.process(keyAluno, lat, lng, timestamp),
                trackingEngine.getLastPosition(keyViagem),
                alunoEmbarqueRedisRepository.buscarPontoEmbarque(viagemId, alunoId)
        ).flatMap(tuple -> {
            TrackingResult resultAluno = tuple.getT1();
            Optional<TrackingState> optViagem = tuple.getT2();
            PontoEmbarque pontoEmbarque = tuple.getT3();

            AlunoPosicaoAtualizadaEvent event = new AlunoPosicaoAtualizadaEvent();
            event.alunoId = alunoId;
            event.viagemId = viagemId;
            event.latitude = resultAluno.latitude();
            event.longitude = resultAluno.longitude();
            event.timestamp = resultAluno.timestamp();

            // Se tem posição do ônibus, calcula distância
            if (optViagem.isPresent()) {
                TrackingState viagem = optViagem.get();
                double distancia = calcularDistancia(
                        lat, lng,
                        viagem.getLastLat(), viagem.getLastLng()
                );
                event.distanciaDoOnibus = distancia / 1000; // converte para km

                // Calcula ETA baseado na velocidade do ônibus
                if (viagem.getLastSpeed() > 0) {
                    double etaHoras = (distancia / 1000) / viagem.getLastSpeed();
                    event.etaParaEmbarque = (long) (etaHoras * 3600);
                }
            }

            // Verifica se está no ponto de embarque correto
            if (pontoEmbarque != null && !pontoEmbarque.isRealizado()) {
                double distanciaAtePonto = calcularDistancia(
                        lat, lng,
                        pontoEmbarque.getLatitude(), pontoEmbarque.getLongitude()
                );

                event.aptoParaEmbarque = distanciaAtePonto < 100; // menos de 100m
                event.pontoEmbarqueId = pontoEmbarque.getPontoId();
                event.tipoEmbarque = pontoEmbarque.getTipo().toString();
                event.distanciaAtePonto = distanciaAtePonto;

                // Se está no ponto e o ônibus está próximo, pode embarcar
                if (event.aptoParaEmbarque && optViagem.isPresent()) {
                    double distanciaOnibus = calcularDistancia(
                            lat, lng,
                            optViagem.get().getLastLat(), optViagem.get().getLastLng()
                    );

                    if (distanciaOnibus < 200) { // Ônibus a menos de 200m
                        event.status = "ONIBUS_PROXIMO";
                    } else {
                        event.status = "AGUARDANDO_ONIBUS";
                    }
                } else {
                    event.status = event.aptoParaEmbarque ? "NO_PONTO" : "FORA_DO_PONTO";
                }
            } else {
                event.status = "SEM_PONTO_DEFINIDO";
            }

            return eventPublisher.publishAluno(event);
        });
    }

    /**
     * Encontra o próximo ponto não visitado da rota
     */
    private Optional<PontoRota> encontrarProximoPonto(List<PontoRota> pontos, double latAtual, double lngAtual) {
        if (pontos == null || pontos.isEmpty()) {
            return Optional.empty();
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
                // Atualiza no Redis
                rotaRedisRepository.atualizarPonto(ponto.getId(), ponto).subscribe();
                continue;
            }

            if (distancia < menorDistancia) {
                menorDistancia = distancia;
                maisProximo = ponto;
            }
        }

        return Optional.ofNullable(maisProximo);
    }

    /**
     * Verifica se chegou em algum ponto e se há alunos
     */
    private Mono<List<AlunoNoPontoInfo>> verificarChegadaEmPonto(
            String viagemId,
            List<PontoRota> pontos,
            Map<String, PontoEmbarque> alunos,
            double latAtual,
            double lngAtual,
            long timestamp) {

        return Flux.fromIterable(pontos)
                .filter(ponto -> !ponto.isVisitado())
                .flatMap(ponto -> {
                    double distancia = calcularDistancia(
                            latAtual, lngAtual,
                            ponto.getLatitude(), ponto.getLongitude()
                    );

                    if (distancia < 100) { // Chegou no ponto
                        ponto.setVisitado(true);
                        ponto.setHorarioChegada(timestamp);

                        // Atualiza no Redis
                        rotaRedisRepository.atualizarPonto(ponto.getId(), ponto).subscribe();

                        // Busca alunos deste ponto
                        return Flux.fromIterable(alunos.values())
                                .filter(p -> p.getPontoId().equals(ponto.getId()) && !p.isRealizado())
                                .map(p -> new AlunoNoPontoInfo(
                                        p.getAlunoId(),
                                        p.getTipo(),
                                        calcularDistancia(latAtual, lngAtual, p.getLatitude(), p.getLongitude())
                                ));
                    }
                    return Flux.empty();
                })
                .collectList();
    }

    /**
     * Verifica zonas de geofencing próximas
     */
    private Mono<String> verificarZonasProximas(
            String viagemId,
            double lat,
            double lng,
            List<PontoRota> pontos,
            Map<String, PontoEmbarque> alunos) {

        return Flux.fromIterable(pontos)
                .flatMap(ponto ->
                        geofencingService.checkPosition(viagemId, lat, lng, "ponto:" + viagemId + ":" + ponto.getId())
                )
                .filter(event -> event.isInside())
                .map(event -> "PROXIMO_PONTO")
                .next()
                .switchIfEmpty(
                        Flux.fromIterable(alunos.values())
                                .flatMap(aluno ->
                                        geofencingService.checkPosition(viagemId, lat, lng,
                                                "aluno:" + viagemId + ":" + aluno.getAlunoId() + ":" + aluno.getTipo())
                                )
                                .filter(event -> event.isInside())
                                .map(event -> "PROXIMO_ALUNO")
                                .next()
                )
                .defaultIfEmpty("");
    }

    /**
     * Cria evento do motorista com todos os dados enriquecidos
     */
    private ViagemPosicaoAtualizadaEvent criarEventoMotorista(
            String viagemId,
            TrackingResult result,
            Tuple4<String, RouteInfo, Optional<Position>, String> tuple,
            BatteryOptimizationService.TrackingConfig batteryConfig,
            Double accuracy,
            String provedor,
            PontoRota proximoPonto
    ) {
        ViagemPosicaoAtualizadaEvent event = new ViagemPosicaoAtualizadaEvent();

        event.viagemId = viagemId;
        event.latitude = result.latitude();
        event.longitude = result.longitude();
        event.timestamp = result.timestamp();
        event.velocidade = result.speed();
        event.endereco = tuple.getT1();
        event.distanciaParaDestino = tuple.getT2().distance();
        event.etaSegundos = tuple.getT2().etaSeconds();
        event.accuracy = accuracy;
        event.provedor = provedor;
        event.bateria = batteryConfig != null ? batteryConfig.batteryLevel() : null;

        // Informações do próximo ponto
        if (proximoPonto != null) {
            event.proximoPontoId = proximoPonto.getId();
            event.proximoPontoNome = proximoPonto.getNome();
            event.proximoPontoTipo = proximoPonto.getTipo().toString();
        }

        // Adiciona metadados
//        if (batteryConfig != null) {
//            event.addMetadata("recommendedInterval", batteryConfig.updateIntervalMs());
//            event.addMetadata("significantMove", batteryConfig.significantMoveMeters());
//        }

//        tuple.getT3().ifPresent(prediction -> {
//            event.addMetadata("nextLat", prediction.lat());
//            event.addMetadata("nextLng", prediction.lng());
//            event.addMetadata("nextEta", prediction.timestamp() - result.timestamp());
//        });
//
//        if (!tuple.getT4().isEmpty()) {
//            event.addMetadata("proximaZona", tuple.getT4());
//        }

        // Determina status
        if (result.speed() < 1) {
            event.status = "PARADO";
        } else if (result.speed() < 10) {
            event.status = "DEVAGAR";
        } else {
            event.status = "EM_ROTA";
        }

        return event;
    }

    /**
     * Calcula distância entre dois pontos em metros
     */
    private double calcularDistancia(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) *
                        Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371000 * c;
    }

    // Record para informação de aluno no ponto
    private record AlunoNoPontoInfo(
            String alunoId,
            PontoEmbarque.TipoEmbarque tipo,
            double distanciaMetros
    ) {
    }
}