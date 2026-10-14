package com.busco.localizacao.infra.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PontoRotaEntityRepository extends JpaRepository<PontoRotaEntity, String> {
    List<PontoRotaEntity> findByViagemIdOrderByOrdem(String viagemId);
}
