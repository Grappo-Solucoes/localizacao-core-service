package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.StopInfo;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class StopDetectionService {

    private final Map<String, StopTracker> stopTrackers = new ConcurrentHashMap<>();
    private static final long STOP_THRESHOLD_MS = 120000; // 2 minutos
    private static final double MOVEMENT_THRESHOLD = 0.5; // 0.5 km/h

    public StopInfo checkStop(String entityId, double speed, long timestamp) {
        StopTracker tracker = stopTrackers.computeIfAbsent(
                entityId, k -> new StopTracker()
        );

        boolean isStopped = speed < MOVEMENT_THRESHOLD;

        if (isStopped) {
            if (tracker.stopStartTime == 0) {
                tracker.stopStartTime = timestamp;
            }

            long stoppedDuration = timestamp - tracker.stopStartTime;

            if (stoppedDuration >= STOP_THRESHOLD_MS && !tracker.notified) {
                tracker.notified = true;
                return new StopInfo(entityId, tracker.stopStartTime, timestamp, stoppedDuration);
            }
        } else {
            // Reset quando começa a se mover
            if (tracker.stopStartTime > 0) {
                tracker.stopStartTime = 0;
                tracker.notified = false;
            }
        }

        return null;
    }

    private static class StopTracker {
        long stopStartTime = 0;
        boolean notified = false;
    }
}