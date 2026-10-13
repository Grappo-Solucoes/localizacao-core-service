package com.busco.localizacao.domain.repository;

import com.busco.localizacao.infra.jpa.PontoEmbarqueEntity;

import java.util.List;
import java.util.Optional;

/**
 * Interface de repositório de PontoEmbarque no domínio.
 */
public interface PontoEmbarqueRepository {

    Optional<PontoEmbarqueEntity> findById(String id);

    List<PontoEmbarqueEntity> findByViagemId(String viagemId);

    List<PontoEmbarqueEntity> findByViagemIdAndRealizadoFalse(String viagemId);

    PontoEmbarqueEntity save(PontoEmbarqueEntity entity);

    void deleteById(String id);
}
