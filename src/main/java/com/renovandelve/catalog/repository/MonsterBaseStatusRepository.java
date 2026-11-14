package com.renovandelve.catalog.repository;

import com.renovandelve.catalog.domain.MonsterBaseStatusEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonsterBaseStatusRepository extends JpaRepository<MonsterBaseStatusEntity, Long> {

    // 스테이지 및 난이도 기반 몬스터 스텟
    Optional<MonsterBaseStatusEntity> findByStageAndDifficulty(short stage, short difficulty);
}
