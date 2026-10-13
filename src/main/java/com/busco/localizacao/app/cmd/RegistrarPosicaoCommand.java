package com.busco.localizacao.app.cmd;

public record RegistrarPosicaoCommand(
    String entidadeId,
    String viagemId,
    double latitude,
    double longitude,
    long timestamp,
    Double batteryLevel,
    Double accuracy,
    String provedor
) {}
