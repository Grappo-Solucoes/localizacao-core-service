package com.busco.localizacao.ui.rest;

import com.busco.localizacao.domain.services.TrackingOrchestratorService;
import com.busco.localizacao.infra.firebase.FirebaseTrackingListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@RestController
@RequestMapping("/api/teste")
public class TesteLocalizacaoController {

    private final FirebaseTrackingListener trackingListener;
    private final TrackingOrchestratorService orchestratorService;
    private final AtomicInteger contador = new AtomicInteger(0);

    public TesteLocalizacaoController(
            FirebaseTrackingListener trackingListener,
            TrackingOrchestratorService orchestratorService) {
        this.trackingListener = trackingListener;
        this.orchestratorService = orchestratorService;
    }

    @PostMapping("/motorista")
    public ResponseEntity<String> testarMotorista() {
        Map<String, Object> raw = criarPosicaoMock(
                -25.3870808,
                -49.1621886,
                "2025/11/07 13:08:38",
                85.5, // battery
                8.5,  // accuracy
                "gps"
        );

        trackingListener.onMotoristaEvent(
                "b5397b61-ed8d-456f-a27f-b7a7663db864",
                raw
        );

        return ResponseEntity.ok("Posição do motorista enviada com sucesso!");
    }

    @PostMapping("/aluno")
    public ResponseEntity<String> testarAluno() {
        Map<String, Object> raw = criarPosicaoMock(
                -25.3881808, // Ponto de embarque do aluno
                -49.1632886,
                "2025/11/07 13:08:45",
                null, null, null
        );

        trackingListener.onAlunoEvent(
                "b5397b61-ed8d-456f-a27f-b7a7663db864",
                "0ea11bc3-5ebe-4b5b-b094-8dbea78629fa",
                raw
        );

        return ResponseEntity.ok("Posição do aluno enviada com sucesso!");
    }

    @PostMapping("/rota-completa")
    public ResponseEntity<String> testarRotaCompleta() {
        StringBuilder log = new StringBuilder();
        log.append("🚀 INICIANDO TESTE DE ROTA COMPLETA\n");
        log.append("====================================\n\n");

        // Sequência de posições do motorista
        Object[][] posicoesMotorista = {
                {-25.3870808, -49.1621886, "Terminal Central", "13:08:38", 85.5},
                {-25.3875808, -49.1626886, "Saindo do Terminal", "13:10:38", 84.2},
                {-25.3880808, -49.1631886, "Chegando Escola 1", "13:15:38", 83.0},
                {-25.3881808, -49.1632886, "Parado na Escola 1", "13:16:38", 82.5},
                {-25.3885808, -49.1636886, "Saindo Escola 1", "13:20:38", 81.8},
                {-25.3890808, -49.1641886, "Chegando Escola 2", "13:25:38", 80.5},
                {-25.3891808, -49.1642886, "Parado na Escola 2", "13:26:38", 80.0},
                {-25.3895808, -49.1646886, "Saindo Escola 2", "13:30:38", 79.2}
        };

        for (int i = 0; i < posicoesMotorista.length; i++) {
            Object[] pos = posicoesMotorista[i];

            Map<String, Object> raw = criarPosicaoMock(
                    (double) pos[0], (double) pos[1],
                    "2025/11/07 " + pos[3],
                    (double) pos[4], // battery
                    5.0 + i, // accuracy
                    "gps"
            );

            log.append(String.format("📍 Posição %d: %s (%.6f, %.6f)\n",
                    i+1, pos[2], (double) pos[0], (double) pos[1]));

            trackingListener.onMotoristaEvent(
                    "b5397b61-ed8d-456f-a27f-b7a7663db864",
                    raw
            );

            // Pequena pausa entre envios
            try { Thread.sleep(300); } catch (Exception e) {}
        }

        log.append("\n✅ Rota do motorista enviada com sucesso!\n");

        return ResponseEntity.ok(log.toString());
    }

    @PostMapping("/aluno-dinamico")
    public ResponseEntity<String> testarAlunoDinamico() {
        StringBuilder log = new StringBuilder();
        log.append("🎓 TESTANDO POSIÇÕES DO ALUNO\n");
        log.append("==============================\n\n");

        // Simula aluno se movendo em direção ao ponto
        Object[][] posicoesAluno = {
                {-25.3885808, -49.1636886, "Distante (200m)", "13:05:00", 200.0, false},
                {-25.3883808, -49.1634886, "A caminho (100m)", "13:06:00", 100.0, false},
                {-25.3882808, -49.1633886, "Próximo (50m)", "13:07:00", 50.0, false},
                {-25.3881808, -49.1632886, "No ponto exato", "13:08:00", 0.0, true},
                {-25.3882008, -49.1633106, "Aguardando (10m)", "13:09:00", 10.0, true}
        };

        for (int i = 0; i < posicoesAluno.length; i++) {
            Object[] pos = posicoesAluno[i];

            Map<String, Object> raw = criarPosicaoMock(
                    (double)pos[0], (double)pos[1],
                    "2025/11/07 " + pos[3],
                    null, null, null
            );

            log.append(String.format("   %s: (%.6f, %.6f) - %s\n",
                    pos[2], (double)pos[0], (double)pos[1], (boolean) pos[5] ? "🎯 NO PONTO!" : "⏳ A caminho"));

            trackingListener.onAlunoEvent(
                    "b5397b61-ed8d-456f-a27f-b7a7663db864",
                    "0ea11bc3-5ebe-4b5b-b094-8dbea78629fa",
                    raw
            );

            try { Thread.sleep(300); } catch (Exception e) {}
        }

        log.append("\n✅ Posições do aluno enviadas com sucesso!\n");

        return ResponseEntity.ok(log.toString());
    }

    @PostMapping("/cenario-completo")
    public ResponseEntity<String> testarCenarioCompleto() {
        StringBuilder log = new StringBuilder();
        log.append("🎯 CENÁRIO COMPLETO - SIMULAÇÃO UBER\n");
        log.append("=====================================\n\n");

        // Cena 1: Motorista no terminal, aluno distante
        log.append("📌 CENA 1: Motorista no terminal, aluno distante\n");
        Map<String, Object> rawMotorista1 = criarPosicaoMock(
                -25.3870808, -49.1621886, "2025/11/07 13:00:00", 100.0, 5.0, "gps"
        );
        trackingListener.onMotoristaEvent("viagem123", rawMotorista1);

        Map<String, Object> rawAluno1 = criarPosicaoMock(
                -25.3885808, -49.1636886, "2025/11/07 13:00:10", null, null, null
        );
        trackingListener.onAlunoEvent("viagem123", "0ea11bc3-5ebe-4b5b-b094-8dbea78629fa", rawAluno1);
        log.append("   ✅ Motorista no terminal | Aluno distante (200m)\n\n");

        try { Thread.sleep(1000); } catch (Exception e) {}

        // Cena 2: Motorista saindo, aluno se aproximando
        log.append("📌 CENA 2: Motorista em rota, aluno se aproximando\n");
        Map<String, Object> rawMotorista2 = criarPosicaoMock(
                -25.3875808, -49.1626886, "2025/11/07 13:05:00", 98.0, 6.0, "gps"
        );
        trackingListener.onMotoristaEvent("viagem123", rawMotorista2);

        Map<String, Object> rawAluno2 = criarPosicaoMock(
                -25.3882808, -49.1633886, "2025/11/07 13:05:10", null, null, null
        );
        trackingListener.onAlunoEvent("viagem123", "0ea11bc3-5ebe-4b5b-b094-8dbea78629fa", rawAluno2);
        log.append("   ✅ Motorista em rota | Aluno próximo (50m)\n\n");

        try { Thread.sleep(1000); } catch (Exception e) {}

        // Cena 3: Motorista chegando na escola, aluno no ponto
        log.append("📌 CENA 3: Motorista chegando, aluno no ponto!\n");
        Map<String, Object> rawMotorista3 = criarPosicaoMock(
                -25.3880808, -49.1631886, "2025/11/07 13:10:00", 95.0, 4.0, "gps"
        );
        trackingListener.onMotoristaEvent("viagem123", rawMotorista3);

        Map<String, Object> rawAluno3 = criarPosicaoMock(
                -25.3881808, -49.1632886, "2025/11/07 13:10:10", null, null, null
        );
        trackingListener.onAlunoEvent("viagem123", "0ea11bc3-5ebe-4b5b-b094-8dbea78629fa", rawAluno3);
        log.append("   ✅ Motorista a 100m da escola | Aluno NO PONTO!\n");
        log.append("   ⚡ ALUNO APTO PARA EMBARQUE!\n\n");

        try { Thread.sleep(1000); } catch (Exception e) {}

        // Cena 4: Embarque realizado
        log.append("📌 CENA 4: Embarque realizado\n");
        Map<String, Object> rawMotorista4 = criarPosicaoMock(
                -25.3880808, -49.1631886, "2025/11/07 13:12:00", 94.0, 4.0, "gps"
        );
        trackingListener.onMotoristaEvent("viagem123", rawMotorista4);
        log.append("   ✅ Aluno embarcado! Seguindo rota...\n\n");

        log.append("🎉 CENÁRIO COMPLETO FINALIZADO!\n");
        log.append("Verifique os logs para ver os eventos publicados.");

        return ResponseEntity.ok(log.toString());
    }

    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> status = new HashMap<>();
        status.put("servidor", "online");
        status.put("timestamp", System.currentTimeMillis());
        status.put("requisicoes", contador.incrementAndGet());
        status.put("mockRedis", "ativo");
        status.put("profile", "test");

        return ResponseEntity.ok(status);
    }

    private Map<String, Object> criarPosicaoMock(
            double lat,
            double lng,
            String timestamp,
            Double battery,
            Double accuracy,
            String provider) {

        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", lat);
        raw.put("longitude", lng);
        raw.put("timeStamp", timestamp);

        if (battery != null) {
            raw.put("batteryLevel", battery);
        }
        if (accuracy != null) {
            raw.put("accuracy", accuracy);
        }
        if (provider != null) {
            raw.put("provider", provider);
        }

        return raw;
    }
}