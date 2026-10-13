package com.busco.localizacao.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PontoEmbarqueJpaRepository extends JpaRepository<PontoEmbarqueEntity, String> {
    List<PontoEmbarqueEntity> findByViagemId(String viagemId);

    List<PontoEmbarqueEntity> findByViagemIdAndRealizadoFalse(String viagemId);
}
