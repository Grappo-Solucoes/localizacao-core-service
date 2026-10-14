package com.busco.localizacao.domain.repository;

import com.busco.localizacao.domain.entity.PontoEmbarque;

import java.util.List;

/**
 * Porta de repositório de {@link PontoEmbarque} no domínio.
 *
 * <p>Expressa o contrato em termos do tipo de domínio (não da entidade JPA) para
 * manter o domínio independente da infraestrutura de persistência (design D2).
 * A implementação vive em {@code infra/repo}, espelhando {@link PontoRotaRepository}.</p>
 */
public interface PontoEmbarqueRepository {

    List<PontoEmbarque> findByViagemId(String viagemId);

    List<PontoEmbarque> findByViagemIdAndRealizadoFalse(String viagemId);
}
