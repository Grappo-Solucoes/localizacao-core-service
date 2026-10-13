package com.busco.localizacao.ui.rest;

import com.busco.localizacao.domain.services.ChegadaVeiculoMonitor;
import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LocalizacaoController {

    private final TrackingOrchestratorService orchestratorService;
    private final ChegadaVeiculoMonitor chegadaVeiculoMonitor;

    public LocalizacaoController(
            TrackingOrchestratorService orchestratorService,
            ChegadaVeiculoMonitor chegadaVeiculoMonitor) {
        this.orchestratorService = orchestratorService;
        this.chegadaVeiculoMonitor = chegadaVeiculoMonitor;
    }

    @PostMapping("/viagens/{viagemId}/posicoes")
    public Mono<ResponseEntity<Map<String, String>>> registrarPosicaoMotorista(
            @PathVariable @NotBlank String viagemId,
            @Valid @RequestBody PosicaoRequest request) {
        return orchestratorService.processMotorista(
                viagemId,
                request.latitude(),
                request.longitude(),
                request.timestamp(),
                request.batteryLevel(),
                request.accuracy(),
                request.provider()
        ).then(Mono.just(ResponseEntity.ok(Map.of("status", "processado", "viagemId", viagemId))));
    }

    @PostMapping("/viagens/{viagemId}/alunos/{alunoId}/posicoes")
    public Mono<ResponseEntity<Map<String, String>>> registrarPosicaoAluno(
            @PathVariable @NotBlank String viagemId,
            @PathVariable @NotBlank String alunoId,
            @Valid @RequestBody PosicaoAlunoRequest request) {
        return orchestratorService.processAluno(
                alunoId,
                viagemId,
                request.latitude(),
                request.longitude(),
                request.timestamp()
        ).then(Mono.just(ResponseEntity.ok(Map.of("status", "processado", "viagemId", viagemId, "alunoId", alunoId))));
    }

    @PostMapping("/viagens/{viagemId}/monitoramento/iniciar")
    public ResponseEntity<Map<String, String>> iniciarMonitoramento(@PathVariable @NotBlank String viagemId) {
        chegadaVeiculoMonitor.iniciarMonitoramento(viagemId);
        return ResponseEntity.ok(Map.of("status", "monitoramento_iniciado", "viagemId", viagemId));
    }

    @PostMapping("/viagens/{viagemId}/monitoramento/finalizar")
    public ResponseEntity<Map<String, String>> finalizarMonitoramento(@PathVariable @NotBlank String viagemId) {
        chegadaVeiculoMonitor.encerrarMonitoramento(viagemId);
        return ResponseEntity.ok(Map.of("status", "monitoramento_finalizado", "viagemId", viagemId));
    }

    public record PosicaoRequest(
            @NotNull Double latitude,
            @NotNull Double longitude,
            @NotNull Long timestamp,
            Double batteryLevel,
            Double accuracy,
            String provider
    ) {}

    public record PosicaoAlunoRequest(
            @NotNull Double latitude,
            @NotNull Double longitude,
            @NotNull Long timestamp
    ) {}
}
