package com.busco.localizacao.domain.repository;

import com.busco.localizacao.domain.entity.PontoRota;

import java.util.List;
import java.util.Optional;

/**
 * Interface de repositório de PontoRota no domínio.
 * Usa a entidade de domínio (PontoRota), não a entidade JPA.
 */
public interface PontoRotaRepository {

    Optional<PontoRota> findById(String id);

    List<PontoRota> findByViagemIdOrderByOrdem(String viagemId);

    PontoRota save(PontoRota ponto);

    void deleteById(String id);
}
