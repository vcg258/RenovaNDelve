package com.renovandelve.catalog.repository;

import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface HelmetRepository extends JpaRepository<HelmetEntity, Long> {
    // 헬멧 단건 조회
    Optional<HelmetEntity> findByNameContains(String name);
}
