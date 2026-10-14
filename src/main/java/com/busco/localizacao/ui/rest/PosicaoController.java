package com.busco.localizacao.ui.rest;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Endpoints de ingestão de posição (motorista e aluno).
 *
 * <p>Caminhos conforme a spec do change
 * {@code substituicao-gradual-localizacao-core-service} (tasks 6.1 e 6.2) e a
 * convenção REST dos serviços irmãos ({@code /api/<recurso>}).</p>
 */
@RestController
@RequestMapping("/api/viagens")
public class PosicaoController {

    private final TrackingOrchestratorService orchestratorService;

    public PosicaoController(TrackingOrchestratorService orchestratorService) {
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/{viagemId}/posicoes")
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

    @PostMapping("/{viagemId}/alunos/{alunoId}/posicoes")
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
        ).then(Mono.just(ResponseEntity.ok(
                Map.of("status", "processado", "viagemId", viagemId, "alunoId", alunoId))));
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
