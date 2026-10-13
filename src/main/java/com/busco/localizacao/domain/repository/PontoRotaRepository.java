package com.busco.localizacao.domain.repository;

import com.busco.localizacao.infra.jpa.PontoRotaEntity;

import java.util.List;
import java.util.Optional;

/**
 * Interface de repositório de PontoRota no domínio.
 */
public interface PontoRotaRepository {

    Optional<PontoRotaEntity> findById(String id);

    List<PontoRotaEntity> findByViagemIdOrderByOrdem(String viagemId);

    PontoRotaEntity save(PontoRotaEntity entity);

    void deleteById(String id);
}
