package com.busco.localizacao.infra.jpa;

import com.busco.localizacao.domain.entity.PontoEmbarque;
import com.busco.localizacao.domain.repository.PontoEmbarqueRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Implementação JPA do repositório de PontoEmbarque.
 * Adapta a entidade JPA (PontoEmbarqueEntity) para a entidade de domínio (PontoEmbarque).
 */
@Repository
public class PontoEmbarqueJpaRepository implements PontoEmbarqueRepository {

    private final PontoEmbarqueEntityRepository entityRepository;

    public PontoEmbarqueJpaRepository(PontoEmbarqueEntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    public List<PontoEmbarque> findByViagemId(String viagemId) {
        return entityRepository.findByViagemId(viagemId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public List<PontoEmbarque> findByViagemIdAndRealizadoFalse(String viagemId) {
        return entityRepository.findByViagemIdAndRealizadoFalse(viagemId).stream()
                .map(this::toDomain)
                .toList();
    }

    private PontoEmbarque toDomain(PontoEmbarqueEntity entity) {
        PontoEmbarque ponto = new PontoEmbarque(
                entity.getAlunoId(),
                entity.getId(),
                entity.getLatitude(),
                entity.getLongitude(),
                toTipo(entity.getTipo())
        );
        if (entity.getHorarioRealizado() != null) {
            ponto.setHorarioRealizado(entity.getHorarioRealizado().toEpochMilli());
        }
        ponto.setRealizado(entity.isRealizado());
        return ponto;
    }

    private static PontoEmbarque.TipoEmbarque toTipo(String tipo) {
        return PontoEmbarque.TipoEmbarque.DESEMBARQUE.name().equalsIgnoreCase(tipo)
                ? PontoEmbarque.TipoEmbarque.DESEMBARQUE
                : PontoEmbarque.TipoEmbarque.EMBARQUE;
    }
}
