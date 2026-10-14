package com.busco.localizacao.domain.exceptions;

public class InvalidPositionException extends TrackingException {
    public InvalidPositionException(String message) {
        super(message);
    }
}
