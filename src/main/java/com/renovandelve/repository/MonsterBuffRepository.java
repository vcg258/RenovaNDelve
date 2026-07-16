package com.renovandelve.repository;

import com.renovandelve.domain.buffentity.MonsterBuffEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MonsterBuffRepository extends JpaRepository<MonsterBuffEntity, Long> {

    // 키워드 기반 몬스터 생성
    Optional<MonsterBuffEntity> findByKeyword(String keyword);
}
