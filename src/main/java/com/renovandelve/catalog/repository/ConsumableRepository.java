package com.renovandelve.catalog.repository;

import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ConsumableRepository extends JpaRepository<ConsumableEntity, Long> {
    // 소모품 단건 조회
    Optional<ConsumableEntity> findByNameContains(String name);

    // 소모품 이름 조회 여러건
}
