package com.renovandelve.catalog.repository;

import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ArmorRepository extends JpaRepository<ArmorEntity, Long> {
    // 갑옷 단건 조회
    Optional<ArmorEntity> findByNameContains(String name);
}
