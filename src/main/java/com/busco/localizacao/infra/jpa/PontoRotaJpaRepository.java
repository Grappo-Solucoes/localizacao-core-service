package com.busco.localizacao.infra.jpa;

import com.busco.localizacao.domain.entity.PontoRota;
import com.busco.localizacao.domain.repository.PontoRotaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Implementação JPA do repositório de PontoRota.
 * Adapta a entidade JPA (PontoRotaEntity) para a entidade de domínio (PontoRota).
 */
@Repository
public class PontoRotaJpaRepository implements PontoRotaRepository {

    private final PontoRotaEntityRepository entityRepository;

    public PontoRotaJpaRepository(PontoRotaEntityRepository entityRepository) {
        this.entityRepository = entityRepository;
    }

    @Override
    public Optional<PontoRota> findById(String id) {
        return entityRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<PontoRota> findByViagemIdOrderByOrdem(String viagemId) {
        return entityRepository.findByViagemIdOrderByOrdem(viagemId).stream()
                .map(this::toDomain)
                .toList();
    }

    @Override
    public PontoRota save(PontoRota ponto) {
        PontoRotaEntity entity = toEntity(ponto);
        PontoRotaEntity saved = entityRepository.save(entity);
        return toDomain(saved);
    }

    @Override
    public void deleteById(String id) {
        entityRepository.deleteById(id);
    }

    private PontoRota toDomain(PontoRotaEntity entity) {
        PontoRota ponto = new PontoRota(
                entity.getId(),
                entity.getNome(),
                entity.getLatitude(),
                entity.getLongitude(),
                PontoRota.TipoPonto.valueOf(entity.getTipo())
        );
        ponto.setVisitado(entity.isVisitado());
        ponto.setHorarioPrevisto(entity.getHorarioPrevisto());
        ponto.setHorarioChegada(entity.getHorarioChegada());
        ponto.setTempoParada(entity.getTempoParada());
        ponto.setOrdem(entity.getOrdem());
        return ponto;
    }

    private PontoRotaEntity toEntity(PontoRota ponto) {
        PontoRotaEntity entity = new PontoRotaEntity();
        entity.setId(ponto.getId());
        entity.setNome(ponto.getNome());
        entity.setLatitude(ponto.getLatitude());
        entity.setLongitude(ponto.getLongitude());
        entity.setTipo(ponto.getTipo().name());
        entity.setVisitado(ponto.isVisitado());
        entity.setHorarioPrevisto(ponto.getHorarioPrevisto());
        entity.setHorarioChegada(ponto.getHorarioChegada());
        entity.setTempoParada(ponto.getTempoParada());
        entity.setOrdem(ponto.getOrdem());
        return entity;
    }
}
