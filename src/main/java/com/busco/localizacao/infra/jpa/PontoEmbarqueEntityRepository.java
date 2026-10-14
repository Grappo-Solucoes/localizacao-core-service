package com.busco.localizacao.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Spring Data repository da entidade JPA {@link PontoEmbarqueEntity}.
 * Consumido apenas pelo adaptador {@link PontoEmbarqueJpaRepository}.
 */
public interface PontoEmbarqueEntityRepository extends JpaRepository<PontoEmbarqueEntity, String> {

    List<PontoEmbarqueEntity> findByViagemId(String viagemId);

    List<PontoEmbarqueEntity> findByViagemIdAndRealizadoFalse(String viagemId);
}
