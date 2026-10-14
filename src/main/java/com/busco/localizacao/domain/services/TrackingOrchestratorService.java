package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.*;
import com.busco.localizacao.domain.events.AlunoPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.events.ViagemPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.vo.GeoPoint;
import com.busco.localizacao.infra.redis.AlunoEmbarqueRedisRepository;
import com.busco.localizacao.infra.redis.RotaRedisRepository;
import com.busco.localizacao.domain.vo.VisaoGeral;
import com.busco.localizacao.domain.vo.Painel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.function.Tuple4;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class TrackingOrchestratorService {

    private static final Logger log = LoggerFactory.getLogger(TrackingOrchestratorService.class);

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
    private final VisaoGeralPublisher visaoGeralSnapshotPublisher;
    private final ChegadaVeiculoMonitor chegadaVeiculoMonitor;

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
            AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepository,
            VisaoGeralPublisher visaoGeralSnapshotPublisher,
            ChegadaVeiculoMonitor chegadaVeiculoMonitor) {
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
        this.visaoGeralSnapshotPublisher = visaoGeralSnapshotPublisher;
        this.chegadaVeiculoMonitor = chegadaVeiculoMonitor;
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
                        return Mono.just(true);
                    }

                    TrackingState previousTracking = optionalState.get();
                    Position current = new Position(new GeoPoint(lat, lng), 0, timestamp);
                    Position previousPosition = new Position(
                            new GeoPoint(previousTracking.getLastLat(), previousTracking.getLastLng()),
                            0,
                            previousTracking.getLastTimestamp()
                    );

                    // Posição inconsistente (timestamp não avança) é descartada:
                    // retorna vazio para interromper o pipeline sem emitir evento.
                    if (!consistencyService.isValid(previousPosition, current)) {
                        return Mono.empty();
                    }
                    return Mono.just(true);
                })
                .flatMap(ignored -> trackingEngine.process(key, lat, lng, timestamp))
                .flatMap(result -> {
                    // Verifica anti-fraude
                    Position pos = new Position(new GeoPoint(lat, lng), result.speed(), timestamp);
                    if (!antiFraudService.isValid(pos)) {
                        return Mono.empty(); // posição suspeita
                    }

                    // Alimenta a detecção de chegada com a posição processada do veículo
                    chegadaVeiculoMonitor.processarPosicaoVeiculo(
                            viagemId, result.latitude(), result.longitude());

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
                                                event = rebuildEventWithStop(event, stop.getStopType());
//                                                event.addMetadata("stopDuration", stop.getDurationMinutes());
                                            }

                                            // Verifica se chegou em algum ponto e marca o ponto como visitado.
                                            // Encadeado (nao fire-and-forget) para que falha do Redis seja
                                            // registrada em vez de descartada, sem derrubar o pipeline.
                                            return verificarChegadaEmPonto(viagemId, pontosRota, alunos, lat, lng, timestamp)
                                                    .doOnError(e -> log.warn(
                                                            "Falha ao verificar chegada em ponto da viagem {}: {}",
                                                            viagemId, e.getMessage()))
                                                    .onErrorResume(e -> Mono.empty())
                                                    .then(eventPublisher.publish(event)
                                                    .doOnSuccess(v -> {
                                                        // Publica snapshot no STOMP para Visão ao Vivo
                                                        try {
                                                            visaoGeralSnapshotPublisher.send(new com.busco.localizacao.domain.vo.VisaoGeral(
                                                                    com.busco.localizacao.domain.vo.Painel.of(0, 0, 0),
                                                                    new java.util.ArrayList<>()
                                                            ));
                                                        } catch (Exception ex) {
                                                            // Nao falha o pipeline se STOMP nao estiver disponivel
                                                        }
                                                    }));
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

            AlunoPosicaoAtualizadaEvent.Builder eventBuilder = AlunoPosicaoAtualizadaEvent.builder()
                    .alunoId(alunoId)
                    .viagemId(viagemId)
                    .latitude(resultAluno.latitude())
                    .longitude(resultAluno.longitude())
                    .timestamp(resultAluno.timestamp());

            // Se tem posição do ônibus, calcula distância
            if (optViagem.isPresent()) {
                TrackingState viagem = optViagem.get();
                double distancia = calcularDistancia(
                        lat, lng,
                        viagem.getLastLat(), viagem.getLastLng()
                );
                eventBuilder.distanciaDoOnibus(distancia / 1000); // converte para km

                // Calcula ETA baseado na velocidade do ônibus
                if (viagem.getLastSpeed() > 0) {
                    double etaHoras = (distancia / 1000) / viagem.getLastSpeed();
                    eventBuilder.etaParaEmbarque((long) (etaHoras * 3600));
                }
            }

            // Verifica se está no ponto de embarque correto
            if (pontoEmbarque != null && !pontoEmbarque.isRealizado()) {
                double distanciaAtePonto = calcularDistancia(
                        lat, lng,
                        pontoEmbarque.getLatitude(), pontoEmbarque.getLongitude()
                );

                eventBuilder.aptoParaEmbarque(distanciaAtePonto < 100) // menos de 100m
                        .pontoEmbarqueId(pontoEmbarque.getPontoId())
                        .tipoEmbarque(pontoEmbarque.getTipo().toString())
                        .distanciaAtePonto(distanciaAtePonto);

                // Se está no ponto e o ônibus está próximo, pode embarcar
                if (distanciaAtePonto < 100 && optViagem.isPresent()) {
                    double distanciaOnibus = calcularDistancia(
                            lat, lng,
                            optViagem.get().getLastLat(), optViagem.get().getLastLng()
                    );

                    if (distanciaOnibus < 200) { // Ônibus a menos de 200m
                        eventBuilder.status("ONIBUS_PROXIMO");
                    } else {
                        eventBuilder.status("AGUARDANDO_ONIBUS");
                    }
                } else {
                    eventBuilder.status(distanciaAtePonto < 100 ? "NO_PONTO" : "FORA_DO_PONTO");
                }
            } else {
                eventBuilder.status("SEM_PONTO_DEFINIDO");
            }

            AlunoPosicaoAtualizadaEvent event = eventBuilder.build();
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
                rotaRedisRepository.atualizarPonto(ponto.getId(), ponto)
                        .doOnError(e -> log.warn(
                                "Falha ao atualizar ponto {} no Redis: {}", ponto.getId(), e.getMessage()))
                        .onErrorResume(e -> Mono.empty())
                        .subscribe();
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

                        // Atualiza no Redis antes de resolver os alunos deste ponto
                        return rotaRedisRepository.atualizarPonto(ponto.getId(), ponto)
                                .doOnError(e -> log.warn(
                                        "Falha ao atualizar ponto {} no Redis: {}", ponto.getId(), e.getMessage()))
                                .onErrorResume(e -> Mono.empty())
                                .thenMany(Flux.fromIterable(alunos.values())
                                        .filter(p -> p.getPontoId().equals(ponto.getId()) && !p.isRealizado())
                                        .map(p -> new AlunoNoPontoInfo(
                                                p.getAlunoId(),
                                                p.getTipo(),
                                                calcularDistancia(latAtual, lngAtual, p.getLatitude(), p.getLongitude())
                                        )));
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
        ViagemPosicaoAtualizadaEvent.Builder eventBuilder = ViagemPosicaoAtualizadaEvent.builder()
                .viagemId(viagemId)
                .latitude(result.latitude())
                .longitude(result.longitude())
                .timestamp(result.timestamp())
                .velocidade(result.speed())
                .endereco(tuple.getT1())
                .distanciaParaDestino(tuple.getT2().distance())
                .etaSegundos(tuple.getT2().etaSeconds())
                .accuracy(accuracy)
                .provedor(provedor)
                .bateria(batteryConfig != null ? batteryConfig.batteryLevel() : null);

        // Informações do próximo ponto
        if (proximoPonto != null) {
            eventBuilder.proximoPontoId(proximoPonto.getId())
                    .proximoPontoNome(proximoPonto.getNome())
                    .proximoPontoTipo(proximoPonto.getTipo().toString());
        }

        // Determina status
        if (result.speed() < 1) {
            eventBuilder.status("PARADO");
        } else if (result.speed() < 10) {
            eventBuilder.status("DEVAGAR");
        } else {
            eventBuilder.status("EM_ROTA");
        }

        return eventBuilder.build();
    }

    /**
     * Recria o evento com novo status (imutabilidade)
     */
    private ViagemPosicaoAtualizadaEvent rebuildEventWithStop(ViagemPosicaoAtualizadaEvent event, String newStatus) {
        return ViagemPosicaoAtualizadaEvent.builder()
                .viagemId(event.getViagemId())
                .latitude(event.getLatitude())
                .longitude(event.getLongitude())
                .timestamp(event.getTimestamp())
                .velocidade(event.getVelocidade())
                .endereco(event.getEndereco())
                .distanciaParaDestino(event.getDistanciaParaDestino())
                .etaSegundos(event.getEtaSegundos())
                .status(newStatus)
                .accuracy(event.getAccuracy())
                .provedor(event.getProvedor())
                .bateria(event.getBateria())
                .proximoPontoId(event.getProximoPontoId())
                .proximoPontoNome(event.getProximoPontoNome())
                .proximoPontoDistancia(event.getProximoPontoDistancia())
                .proximoPontoETA(event.getProximoPontoETA())
                .proximoPontoTipo(event.getProximoPontoTipo())
                .build();
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
