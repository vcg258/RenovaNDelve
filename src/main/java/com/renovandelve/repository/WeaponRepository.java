package com.renovandelve.repository;

import com.renovandelve.domain.weaponentity.WeaponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface WeaponRepository extends JpaRepository<WeaponEntity, Long> {
    // 무기 단건 조회
    Optional<WeaponEntity> findByNameContains(String name);
}
