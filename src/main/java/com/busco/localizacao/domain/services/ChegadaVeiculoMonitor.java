package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.events.ViagemChegadaEvent;
import com.busco.localizacao.infra.jpa.PontoEmbarqueEntity;
import com.busco.localizacao.infra.jpa.PontoEmbarqueJpaRepository;
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

    private final PontoEmbarqueJpaRepository pontoEmbarqueJpaRepository;
    private final TrackingEventPublisher eventPublisher;

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
            PontoEmbarqueJpaRepository pontoEmbarqueJpaRepository,
            TrackingEventPublisher eventPublisher) {
        this.pontoEmbarqueJpaRepository = pontoEmbarqueJpaRepository;
        this.eventPublisher = eventPublisher;
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
        List<PontoEmbarqueEntity> pontos = pontoEmbarqueJpaRepository.findByViagemIdAndRealizadoFalse(viagemId);
        if (pontos == null || pontos.isEmpty()) {
            return null;
        }

        Map<String, PontoChegada> pontosPorChave = new LinkedHashMap<>();
        for (PontoEmbarqueEntity ponto : pontos) {
            String chave = ponto.getLatitude() + "," + ponto.getLongitude();
            PontoChegada pontoChegada = pontosPorChave.computeIfAbsent(chave,
                    k -> new PontoChegada(k, ponto.getId(), ponto.getNome(),
                            ponto.getLatitude(), ponto.getLongitude()));
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
                if (monitoresAtivos.remove(viagemId) != null) {
                    destruir();
                    log.warn("Monitoramento de chegada para a viagem {} encerrado por TTL ({} min)",
                            viagemId, ttlMinutos);
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

        private void removerPonto(String chave) {
            pontos.removeIf(p -> p.getChave().equals(chave));
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
        log.info("Chegada notificada no ponto {} ({}) da viagem {} ({} alunos)",
                ponto.getPontoId(), ponto.getChave(), monitor.getViagemId(), ponto.getAlunos().size());
        monitor.removerPonto(ponto.getChave());

        ViagemChegadaEvent event = new ViagemChegadaEvent(
                monitor.getViagemId(),
                ponto.getPontoId(),
                ponto.getPontoNome(),
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
        private final String chave;
        private final String pontoId;
        private final String pontoNome;
        private final double latitude;
        private final double longitude;
        private final Set<String> alunos = new HashSet<>();

        private PontoChegada(String chave, String pontoId, String pontoNome,
                             double latitude, double longitude) {
            this.chave = chave;
            this.pontoId = pontoId;
            this.pontoNome = pontoNome;
            this.latitude = latitude;
            this.longitude = longitude;
        }

        private void addAluno(String alunoId) { alunos.add(alunoId); }
        private String getChave() { return chave; }
        private String getPontoId() { return pontoId; }
        private String getPontoNome() { return pontoNome; }
        private double getLatitude() { return latitude; }
        private double getLongitude() { return longitude; }
        private Set<String> getAlunos() { return alunos; }
    }
}
