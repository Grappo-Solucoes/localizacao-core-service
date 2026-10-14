package com.busco.localizacao.e2e;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.entity.TrackingState;
import com.busco.localizacao.domain.events.AlunoPosicaoAtualizadaEvent;
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
    private GeofencingService geofencingService;
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
        geofencingService = new GeofencingService();

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
                geofencingService,
                new StopDetectionService(),
                new RoutePredictionService(),
                new BatteryOptimizationService(),
                antiFraud,
                new TrackingConsistencyService(),
                rotaRedisRepository,
                alunoEmbarqueRedisRepository,
                new RotaIntegrationService(rotaRedisRepository, alunoEmbarqueRedisRepository, geofencingService),
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
    void geocodificacaoIndisponivel_publicaEventoSemEndereco() {
        // Spec localizacao/enriquecimento ("Geocodificacao indisponivel"): o evento e
        // publicado SEM endereco, sem interromper o pipeline. Gate do Optional no zip:
        // se o Mono de endereco vazio derrubasse o Mono.zip, nenhum evento sairia.
        when(geocodingService.reverseGeocode(anyDouble(), anyDouble())).thenReturn(Mono.empty());

        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());

        ViagemPosicaoAtualizadaEvent evento = captor.getValue();
        assertNull(evento.getEndereco());
        // Restante do enriquecimento continua intacto
        assertEquals(VIAGEM_ID, evento.getViagemId());
        assertEquals("ponto-1", evento.getProximoPontoId());
        assertEquals("PARADO", evento.getStatus());
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
        // Contrato real do GeocodingService quando o provedor falha: Mono VAZIO (nunca
        // texto sentinela). O evento sai sem endereco.
        when(geocodingService.reverseGeocode(anyDouble(), anyDouble()))
                .thenReturn(Mono.empty());

        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertNull(captor.getValue().getEndereco());
    }

    @Test
    void chaveDeRotaAusenteNoRedis_publicaEvento() {
        // GET de chave inexistente no Redis devolve Mono VAZIO (nao Mono de lista vazia).
        // Antes: o Mono.zip com essa fonte ficava vazio e a posicao era descartada em
        // silencio, com 200 no REST e nenhum evento publicado.
        when(rotaRedisRepository.buscarPontosRota(VIAGEM_ID)).thenReturn(Mono.empty());

        orchestrator.processMotorista(
                VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertNull(captor.getValue().getProximoPontoId());
        assertEquals(VIAGEM_ID, captor.getValue().getViagemId());
    }

    @Test
    void posicaoAlunoSemEmbarcacaoRegistrada_publicaEvento() {
        // HGET de campo inexistente tambem devolve Mono VAZIO: o aluno sem embarque no
        // hash (ou sem embarque cadastrado) nao podia perder o evento.
        when(eventPublisher.publishAluno(any())).thenReturn(Mono.empty());
        when(alunoEmbarqueRedisRepository.buscarPontoEmbarque(VIAGEM_ID, "aluno-sem-embarque"))
                .thenReturn(Mono.empty());

        orchestrator.processAluno(
                "aluno-sem-embarque", VIAGEM_ID, -23.5505, -46.6333, 1_700_000_000_000L
        ).block();

        ArgumentCaptor<AlunoPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(AlunoPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publishAluno(captor.capture());
        assertEquals("SEM_PONTO_DEFINIDO", captor.getValue().getStatus());
        assertEquals("aluno-sem-embarque", captor.getValue().getAlunoId());
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

    @Test
    void posicaoDentroDe100mDoPonto_marcaPontoComoVisitadoNoRedis() {
        // posicao exatamente sobre o ponto da rota configurado no setUp (-23.5600,-46.6400)
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5600, -46.6400, 1_700_000_000_000L, null, null, null
        ).block();

        // a marcacao efetiva acontece em encontrarProximoPonto (usada para ETA/proximo ponto)
        verify(rotaRedisRepository).atualizarPonto(eq("ponto-1"), argThat(PontoRota::isVisitado));
        verify(eventPublisher).publish(any());
    }

    @Test
    void falhaAoMarcarPontoVisitadoNoRedis_naoImpedePublicacaoDoEvento() {
        when(rotaRedisRepository.atualizarPonto(anyString(), any()))
                .thenReturn(Mono.error(new RuntimeException("Redis indisponivel")));

        assertDoesNotThrow(() -> orchestrator.processMotorista(
                VIAGEM_ID, -23.5600, -46.6400, 1_700_000_000_000L, null, null, null).block());

        verify(eventPublisher).publish(any());
    }

    @Test
    void posicaoDentroDaZonaDePonto_incluiProximaZonaNoEvento() {
        // posicao sobre o ponto da rota: dentro da zona de 100 m registrada para ele
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5600, -46.6400, 1_700_000_000_000L, null, null, null
        ).block();

        ViagemPosicaoAtualizadaEvent evento = eventoPublicado();
        assertEquals("PROXIMO_PONTO", evento.getMetadata().get("proximaZona"));
    }

    @Test
    void posicaoDentroDaZonaDeEmbarque_incluiProximaZonaDeAlunoNoEvento() {
        PontoEmbarque embarque = new PontoEmbarque(
                "aluno-1", "ponto-1", -23.5700, -46.6500, PontoEmbarque.TipoEmbarque.EMBARQUE);
        when(alunoEmbarqueRedisRepository.buscarMapaEmbarques(VIAGEM_ID))
                .thenReturn(Mono.just(Map.of("aluno-1", embarque)));

        // ~1,5 km do ponto da rota (fora da zona dele), dentro da zona de 50 m do embarque
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5700, -46.6500, 1_700_000_000_000L, null, null, null
        ).block();

        ViagemPosicaoAtualizadaEvent evento = eventoPublicado();
        assertEquals("PROXIMO_ALUNO", evento.getMetadata().get("proximaZona"));
    }

    @Test
    void posicaoForaDeTodasAsZonas_publicaEventoSemZonaProxima() {
        orchestrator.processMotorista(
                VIAGEM_ID, -23.6000, -46.7000, 1_700_000_000_000L, null, null, null
        ).block();

        ViagemPosicaoAtualizadaEvent evento = eventoPublicado();
        assertTrue(evento.getMetadata() == null
                || !evento.getMetadata().containsKey("proximaZona"));
    }

    @Test
    void paradaDetectada_preservaZonaNoEvento() {
        long inicio = 1_700_000_000_000L;
        orchestrator.processMotorista(
                VIAGEM_ID, -23.5600, -46.6400, inicio, null, null, null
        ).block();
        // parado por mais de 2 minutos (limiar do StopDetectionService)
        orchestrator.processMotorista(
                VIAGEM_ID, -23.56005, -46.64005, inicio + 130_000L, null, null, null
        ).block();

        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher, times(2)).publish(captor.capture());

        ViagemPosicaoAtualizadaEvent comParada = captor.getAllValues().get(1);
        assertNotEquals("PARADO", comParada.getStatus());
        assertEquals("PROXIMO_PONTO", comParada.getMetadata().get("proximaZona"));
    }

    private ViagemPosicaoAtualizadaEvent eventoPublicado() {
        ArgumentCaptor<ViagemPosicaoAtualizadaEvent> captor =
                ArgumentCaptor.forClass(ViagemPosicaoAtualizadaEvent.class);
        verify(eventPublisher).publish(captor.capture());
        return captor.getValue();
    }
}
