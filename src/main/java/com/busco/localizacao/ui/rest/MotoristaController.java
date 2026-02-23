package com.busco.localizacao.ui.rest;

import com.busco.localizacao.infra.firebase.FirebaseTrackingListener;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/localizacao/motorista")
public class MotoristaController {

    private final FirebaseTrackingListener trackingListener;

    public MotoristaController(FirebaseTrackingListener trackingListener) {
        this.trackingListener = trackingListener;
    }

    @PostMapping
    public ResponseEntity<Void> testarLocalizacao() throws Exception {
        Map<String, Object> raw = new HashMap<>();
        raw.put("latitude", -25.3870808);
        raw.put("longitude", -49.1621886);
        raw.put("timeStamp", "2025/11/07 13:08:38");

        trackingListener.onMotoristaEvent(
                "b5397b61-ed8d-456f-a27f-b7a7663db864",
                raw
        );

        trackingListener.onAlunoEvent(
                "b5397b61-ed8d-456f-a27f-b7a7663db864",
                "0ea11bc3-5ebe-4b5b-b094-8dbea78629fa",
                raw
        );

        return ResponseEntity.ok().build();
    }
}
