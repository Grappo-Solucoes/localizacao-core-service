package com.busco.localizacao.ui.rest;

import com.busco.localizacao.domain.services.ChegadaVeiculoMonitor;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Endpoints de ciclo de vida do monitoramento de chegada.
 *
 * <p>Caminhos conforme a spec do change
 * {@code substituicao-gradual-localizacao-core-service} (tasks 6.3 e 6.4).</p>
 */
@RestController
@RequestMapping("/api/viagens")
public class MonitoramentoController {

    private final ChegadaVeiculoMonitor chegadaVeiculoMonitor;

    public MonitoramentoController(ChegadaVeiculoMonitor chegadaVeiculoMonitor) {
        this.chegadaVeiculoMonitor = chegadaVeiculoMonitor;
    }

    @PostMapping("/{viagemId}/monitoramento/iniciar")
    public ResponseEntity<Map<String, String>> iniciarMonitoramento(@PathVariable @NotBlank String viagemId) {
        chegadaVeiculoMonitor.iniciarMonitoramento(viagemId);
        return ResponseEntity.ok(Map.of("status", "monitoramento_iniciado", "viagemId", viagemId));
    }

    @PostMapping("/{viagemId}/monitoramento/finalizar")
    public ResponseEntity<Map<String, String>> finalizarMonitoramento(@PathVariable @NotBlank String viagemId) {
        chegadaVeiculoMonitor.encerrarMonitoramento(viagemId);
        return ResponseEntity.ok(Map.of("status", "monitoramento_finalizado", "viagemId", viagemId));
    }
}
