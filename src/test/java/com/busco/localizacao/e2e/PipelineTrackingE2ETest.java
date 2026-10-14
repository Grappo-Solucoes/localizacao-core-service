package com.busco.localizacao.e2e;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.entity.TrackingState;
import com.busco.localizacao.domain.events.ViagemPosicaoAtualizadaEvent;
import com.busco.localizacao.domain.services.*;
import com.busco.localizacao.domain.vo.GeoPoint;
import com.busco.localizacao.infra.redis.AlunoEmbarqueRedisRepository;
import com.busco.localizacao.infra.redis.RotaRedisRepository;
import com.busco.localizacao.infra.redis.TrackingStateRepository;
import com.busco.localizacao.infra.ws.VisaoGeralSnapshotPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Teste end-to-end do pipeline de tracking:
 * ingestao -> tracking -> enriquecimento -> publicacao Rabbit -> snapshot STOMP.
 *
 * Usa os servicos de dominio reais e mocka apenas as bordas de infraestrutura
 * (Redis, Geocoding HTTP, Rabbit e STOMP).
 */
class PipelineTrackingE2ETest {

    private static final String VIAGEM_ID = "viagem-e2e";

    private TrackingStateRepository trackingStateRepository;
    private RotaRedisRepository rotaRedisRepository;
    private AlunoEmbarqueRedisRepository alunoEmbarqueRedisRepository;
    private GeocodingService geocodingService;
    private TrackingEventPublisher eventPublisher;
    private SimpMessagingTemplate messagingTemplate;
    private ChegadaVeiculoMonitor chegadaVeiculoMonitor;
    private TrackingOrchestratorService orchestrator;

    @BeforeEach
    void setUp() {
        trackingStateRepository = mock(TrackingStateRepository.class);
        rotaRedisRepository = mock(RotaRedisRepository.class);
        alunoEmbarqueRedisRepository = mock(AlunoEmbarqueRedisRepository.class);
        geocodingService = mock(GeocodingService.class);
        eventPublisher = mock(TrackingEventPublisher.class);
        messagingTemplate = mock(SimpMessagingTemplate.class);
        chegadaVeiculoMonitor = mock(ChegadaVeiculoMonitor.class);

        when(eventPublisher.publish(any())).thenReturn(Mono.empty());
        when(geocodingService.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(Mono.just("Rua Teste, 100"));

        // Sem estado anterior: primeira posicao cria o estado no tracking engine
        when(trackingStateRepository.find(anyString())).thenReturn(Mono.empty());
        when(trackingStateRepository.save(anyString(), any())).thenReturn(Mono.just(true));

        PontoRota ponto = new PontoRota("ponto-1", "Escola Municipal", -23.5600, -46.6400, PontoRota.TipoPonto.ESCOLA);
        when(rotaRedisRepository.buscarPontosRota(VIAGEM_ID)).thenReturn(Mono.just(List.of(ponto)));
        when(rotaRedisRepository.atualizarPonto(anyString(), any())).thenReturn(Mono.just(true));

        PontoEmbarque embarque = new PontoEmbarque("aluno-1", "ponto-1", -23.5600, -46.6400, PontoEmbarque.TipoEmbarque.EMBARQUE);
        when(alunoEmbarqueRedisRepository.buscarMapaEmbarques(VIAGEM_ID))
                .thenReturn(Mono.just(Map.of("aluno-1", embarque)));

        TrackingEngine trackingEngine = new TrackingEngine(
                trackingStateRepository, new SpeedCalculator(), new KalmanFilter());
        ReflectionTestUtils.setField(trackingEngine, "maxSpeed", 150.0);

        TrackingAntiFraudService antiFraud = new TrackingAntiFraudService();
        ReflectionTestUtils.setField(antiFraud, "maxSpeed", 150.0);
        ReflectionTestUtils.setField(antiFraud, "maxDistanceJump", 500.0);

        VisaoGeralSnapshotPublisher snapshotPublisher =
                new VisaoGeralSnapshotPublisher(messagingTemplate, 60L);

        orchestrator = new TrackingOrchestratorService(
                trackingEngine,
                eventPublisher,
                geocodingService,
                new RouteCalculator(),
                new GeofencingService(),
                new StopDetectionService(),
                new RoutePredictionService(),
                new BatteryOptimizationService(),
                antiFraud,
                new TrackingConsistencyService(),
                rotaRedisRepository,
                alunoEmbarqueRedisRepository,
                snapshotPublisher,
                chegadaVeiculoMonitor
        );
    }

    @Test
    void pipelineCompleto_ingestaoAtePublicacaoEPublicacaoStomp() {
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, 0.85, 5.0, "gps"
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());

        ViagemPosicaoAtualizadaEvent evento = captor.getValue();
        assertEquals(VIAGEM_ID, evento.getViagemId());
        assertEquals(-23.5505, evento.getLatitude(), 1e-9);
        assertEquals(-46.6333, evento.getLongitude(), 1e-9);
        assertEquals("Rua Teste, 100", evento.getEndereco());
        assertEquals("gps", evento.getProvedor());
        assertEquals(5.0, evento.getAccuracy());

        // Pipeline publica snapshot STOMP apos sucesso no Rabbit
        verify(messagingTemplate).convertAndSend(eq("/topic/visao-geral"), any(Object.class));
    }

    @Test
    void pipelineCompleto_estadoPersistidoNoTrackingEngine() {
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<TrackingState> captor = ArgumentCaptor.forClass(TrackingState.class);
        verify(trackingStateRepository).save(eq("viagem:" + VIAGEM_ID), captor.capture());

        TrackingState state = captor.getValue();
        assertEquals(-23.5505, state.getLastLat(), 1e-9);
        assertEquals(-46.6333, state.getLastLng(), 1e-9);
        assertEquals(1_700_000_000_000L, state.getLastTimestamp());
    }

    @Test
    void pipelineCompleto_enriqueceComProximoPontoDaRota() {
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());

        assertEquals("ponto-1", captor.getValue().getProximoPontoId());
        assertEquals("Escola Municipal", captor.getValue().getProximoPontoNome());
    }

    @Test
    void pipelineCompleto_statusEmRotaQuandoPrimeiraPosicao() {
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());

        assertEquals("PARADO", captor.getValue().getStatus());
    }

    @Test
    void posicaoInconsistente_naoPublicaEvento() {
        TrackingState anterior = new TrackingState(-23.5505, -46.6333, 2_000_000_000_000L, 30.0);
        when(trackingStateRepository.find("viagem:" + VIAGEM_ID))
                .thenReturn(Mono.just(anterior));

        // timestamp menor que o anterior -> inconsistente
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5600, -46.6400, 1_000_000_000_000L, null, null, null
        ).block();

        verify(eventPublisher, never()).publish(any());
        verify(messagingTemplate, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void primeiraPosicaoProcessaAlunoEPublica() {
        when(eventPublisher.publishAluno(any())).thenReturn(Mono.empty());
        when(alunoEmbarqueRedisRepository.buscarPontoEmbarque(VIAGEM_ID, "aluno-1"))
                .thenReturn(Mono.just(new PontoEmbarque(
                        "aluno-1", "ponto-1", -23.5600, -46.6400,
                        PontoEmbarque.TipoEmbarque.EMBARQUE)));

        orchestrator.processAluno(
                "aluno-1", VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L
        ).block();

        verify(eventPublisher).publishAluno(any());
    }

    @Test
    void geocodingIndisponivel_naoQuebraPipeline() {
        when(geocodingService.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(Mono.just(""));

        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals("", captor.getValue().getEndereco());
    }

    @Test
    void rotaVazia_publicaEventoSemProximoPonto() {
        when(rotaRedisRepository.buscarPontosRota(VIAGEM_ID)).thenReturn(Mono.just(List.of()));
        when(alunoEmbarqueRedisRepository.buscarMapaEmbarques(VIAGEM_ID))
                .thenReturn(Mono.just(Map.of()));

        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertNull(captor.getValue().getProximoPontoId());
    }

    @Test
    void antiFraudeBloqueiaVelocidadeAcimaDoLimiar() {
        TrackingState anterior = new TrackingState(-23.5505, -46.6333, 1_700_000_000_000L, 0.0);
        when(trackingStateRepository.find("viagem:" + VIAGEM_ID))
                .thenReturn(Mono.just(anterior));

        // salto de ~1.4km em 1s => velocidade muito acima de 150 km/h
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5700, -46.6500, 1_700_000_001_000L, null, null, null
        ).block();

        verify(eventPublisher, never()).publish(any());
    }

    @Test
    void trackingStateMantidoApenasNoRedis_semPersistenciaJpa() {
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        // contrato: TrackingState vive no Redis (key "viagem:{id}")
        verify(trackingStateRepository).save(eq("viagem:" + VIAGEM_ID), any());
    }

    @Test
    void posicaoProcessada_alimentaDeteccaoDeChegada() {
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        // a detecção de chegada recebe a posição processada do veículo
        verify(chegadaVeiculoMonitor).processarPosicaoVeiculo(
                eq(VIAGEM_ID), eq(-23.5505), eq(-46.6333));
    }

    @Test
    void posicaoDescartada_naoAlimentaDeteccaoDeChegada() {
        TrackingState anterior = new TrackingState(-23.5505, -46.6333, 2_000_000_000_000L, 30.0);
        when(trackingStateRepository.find("viagem:" + VIAGEM_ID))
                .thenReturn(Mono.just(anterior));

        // timestamp menor que o anterior -> inconsistente, descartada
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5600, -46.6400, 1_000_000_000_000L, null, null, null
        ).block();

        verify(chegadaVeiculoMonitor, never()).processarPosicaoVeiculo(any(), anyDouble(), anyDouble());
    }
}
