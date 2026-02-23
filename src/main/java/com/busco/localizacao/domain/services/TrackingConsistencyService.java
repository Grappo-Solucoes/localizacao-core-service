package com.busco.localizacao.domain.services;

import com.busco.localizacao.domain.entity.Position;
import org.springframework.stereotype.Service;

@Service
public class TrackingConsistencyService {

    public boolean isValid(
            Position previous,
            Position current
    ) {

        if (previous == null)
            return true;

        long delta =
                current.getTimestamp() -
                        previous.getTimestamp();

        if (delta <= 0)
            return false;

        return true;
    }
}