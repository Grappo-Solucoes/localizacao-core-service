package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.events.ViagemChegadaEvent;
import com.busco.localizacao.domain.repository.PontoEmbarqueRepository;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;

@Service
public class ChegadaVeiculoMonitor {

    private static final Logger log = LoggerFactory.getLogger(ChegadaVeiculoMonitor.class);

    private final PontoEmbarqueRepository pontoEmbarqueRepository;
    private final TrackingEventPublisher eventPublisher;
    private final GeofencingService geofencingService;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "chegada-veiculo-ttl");
        t.setDaemon(true);
        return t;
    });

    @Value("${app.chegada.distancia-metros:300}")
    private double raioChegadaMetros;

    @Value("${app.chegada.ttl-minutos:120}")
    private long ttlMinutos;

    private final Map<String, Monitoragem> monitoresAtivos = new ConcurrentHashMap<>();

    public ChegadaVeiculoMonitor(
            PontoEmbarqueRepository pontoEmbarqueRepository,
            TrackingEventPublisher eventPublisher,
            GeofencingService geofencingService) {
        this.pontoEmbarqueRepository = pontoEmbarqueRepository;
        this.eventPublisher = eventPublisher;
        this.geofencingService = geofencingService;
    }

    public void iniciarMonitoramento(String viagemId) {
        try {
            Monitoragem monitor = montarMonitor(viagemId);
            if (monitor == null) {
                return;
            }
            monitoresAtivos.put(viagemId, monitor);
            monitor.agendarTtl();
            log.info("Monitoramento de chegada iniciado para a viagem {} ({} ponto(s))",
                    viagemId, monitor.getPontos().size());
        } catch (Exception e) {
            log.warn("Falha ao iniciar monitoramento de chegada para a viagem {}: {}",
                    viagemId, e.getMessage());
        }
    }

    public void encerrarMonitoramento(String viagemId) {
        Monitoragem monitor = monitoresAtivos.remove(viagemId);
        if (monitor != null) {
            monitor.destruir();
            log.info("Monitoramento de chegada encerrado para a viagem {}", viagemId);
        }
        // Fim da viagem: descarta as zonas de geofence registradas para ela. Fora do if
        // porque as posicoes podem ter registrado zonas sem `iniciarMonitoramento`. O
        // registro e refeito a cada posicao, entao isso nao desliga o geofence de viagem
        // ativa e evita crescimento indefinido do mapa.
        geofencingService.removerZonasDaViagem(viagemId);
    }

    public void processarPosicaoVeiculo(String viagemId, double lat, double lng) {
        Monitoragem monitor = monitoresAtivos.get(viagemId);
        if (monitor != null) {
            monitor.processarPosicao(lat, lng);
        }
    }

    @PreDestroy
    public void encerrar() {
        monitoresAtivos.values().forEach(Monitoragem::destruir);
        monitoresAtivos.clear();
        scheduler.shutdownNow();
    }

    private Monitoragem montarMonitor(String viagemId) {
        List<PontoEmbarque> pontos = pontoEmbarqueRepository.findByViagemIdAndRealizadoFalse(viagemId);
        if (pontos == null || pontos.isEmpty()) {
            return null;
        }

        Map<String, PontoChegada> pontosPorChave = new LinkedHashMap<>();
        for (PontoEmbarque ponto : pontos) {
            String chave = ponto.getLatitude() + "," + ponto.getLongitude();
            PontoChegada pontoChegada = pontosPorChave.computeIfAbsent(chave,
                    k -> new PontoChegada(k, ponto.getLatitude(), ponto.getLongitude()));
            pontoChegada.addAluno(ponto.getAlunoId());
        }

        if (pontosPorChave.isEmpty()) {
            return null;
        }
        return new Monitoragem(viagemId, new ArrayList<>(pontosPorChave.values()));
    }

    static double distanciaMetros(double lat1, double lng1, double lat2, double lng2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return 6371.0 * c * 1000.0;
    }

    private class Monitoragem {
        private final String viagemId;
        private final List<PontoChegada> pontos;
        private ScheduledFuture<?> ttlTask;

        private Monitoragem(String viagemId, List<PontoChegada> pontos) {
            this.viagemId = viagemId;
            this.pontos = new CopyOnWriteArrayList<>(pontos);
        }

        private void agendarTtl() {
            ttlTask = scheduler.schedule(() -> {
                if (monitoresAtivos.containsKey(viagemId)) {
                    log.warn("Monitoramento de chegada para a viagem {} encerrado por TTL ({} min)",
                            viagemId, ttlMinutos);
                    encerrarMonitoramento(viagemId);
                }
            }, ttlMinutos, TimeUnit.MINUTES);
        }

        private void processarPosicao(double lat, double lng) {
            List<PontoChegada> aNotificar = new ArrayList<>();
            for (PontoChegada ponto : pontos) {
                if (distanciaMetros(lat, lng, ponto.getLatitude(), ponto.getLongitude()) <= raioChegadaMetros) {
                    aNotificar.add(ponto);
                }
            }
            aNotificar.forEach(ponto -> notificarPonto(this, ponto));
            if (pontos.isEmpty()) {
                encerrarMonitoramento(viagemId);
            }
        }

        private void removerPonto(String idPonto) {
            pontos.removeIf(p -> p.getId().equals(idPonto));
        }

        private void destruir() {
            cancelarTtl();
        }

        private void cancelarTtl() {
            if (ttlTask != null) {
                ttlTask.cancel(false);
                ttlTask = null;
            }
        }

        private String getViagemId() { return viagemId; }
        private List<PontoChegada> getPontos() { return pontos; }
    }

    private void notificarPonto(Monitoragem monitor, PontoChegada ponto) {
        int raioMetros = (int) Math.round(raioChegadaMetros);
        log.info("Chegada notificada no ponto {} da viagem {} ({} alunos)",
                ponto.getId(), monitor.getViagemId(), ponto.getAlunos().size());
        monitor.removerPonto(ponto.getId());

        ViagemChegadaEvent event = new ViagemChegadaEvent(
                monitor.getViagemId(),
                ponto.getId(),
                ponto.getId(),
                ponto.getLatitude(),
                ponto.getLongitude(),
                System.currentTimeMillis(),
                ponto.getAlunos()
        );
        eventPublisher.publishChegada(event)
                .subscribe(
                        null,
                        err -> log.warn("Falha ao publicar evento de chegada: {}", err.getMessage())
                );
    }

    private static class PontoChegada {
        private final String id;
        private final double latitude;
        private final double longitude;
        private final Set<String> alunos = new HashSet<>();

        private PontoChegada(String id, double latitude, double longitude) {
            this.id = id;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        private void addAluno(String alunoId) { alunos.add(alunoId); }
        private String getId() { return id; }
        private double getLatitude() { return latitude; }
        private double getLongitude() { return longitude; }
        private Set<String> getAlunos() { return alunos; }
    }
}
