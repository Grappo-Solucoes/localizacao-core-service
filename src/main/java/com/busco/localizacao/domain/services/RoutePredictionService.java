package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.Position;
import com.busco.localizacao.domain.vo.GeoPoint;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RoutePredictionService {

    private final Map<String, Deque<Position>> positionHistory = new ConcurrentHashMap<>();
    private static final int HISTORY_SIZE = 10;

    public void addPosition(String entityId, double lat, double lng, long timestamp) {
        positionHistory.computeIfAbsent(entityId, k -> new LinkedList<>())
                .addLast(new Position(new GeoPoint(lat, lng), timestamp));

        // Mantém apenas últimas posições
        Deque<Position> history = positionHistory.get(entityId);
        while (history.size() > HISTORY_SIZE) {
            history.removeFirst();
        }
    }

    public Optional<Position> predictNextPosition(String entityId) {
        Deque<Position> history = positionHistory.get(entityId);
        if (history == null || history.size() < 3) {
            return Optional.empty();
        }

        List<Position> positions = new ArrayList<>(history);

        // Calcula vetor de movimento médio
        double avgLatDelta = 0;
        double avgLngDelta = 0;
        long avgTimeDelta = 0;

        for (int i = 1; i < positions.size(); i++) {
            Position p1 = positions.get(i-1);
            Position p2 = positions.get(i);
            avgLatDelta += p2.getPoint().latitude() - p1.getPoint().latitude();
            avgLngDelta += p2.getPoint().longitude() - p1.getPoint().longitude();
            avgTimeDelta += p2.getTimestamp() - p1.getTimestamp();
        }

        int count = positions.size() - 1;
        avgLatDelta /= count;
        avgLngDelta /= count;
        avgTimeDelta /= count;

        Position last = positions.get(positions.size() - 1);

        return Optional.of(new Position(
                new GeoPoint(last.getPoint().latitude() + avgLatDelta,
                        last.getPoint().longitude() + avgLngDelta),
                last.getTimestamp() + avgTimeDelta
        ));
    }

}