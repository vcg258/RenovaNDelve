package com.renovandelve.catalog.repository;

import com.renovandelve.catalog.domain.MonsterEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MonsterRepository extends JpaRepository<MonsterEntity, Long> {
    // 몬스터 이름으로 조회 (일반)
    @Query("SELECT m FROM MonsterEntity m WHERE m.monsterType != 2 AND m.name IN :name")
    List<MonsterEntity> findByNameNormal(@Param("name") List<String> name);

    // 몬스터 이름으로 조회 (보스)
    @Query("SELECT m FROM MonsterEntity m WHERE m.monsterType = 2 AND m.name IN :name")
    List<MonsterEntity> findByNameBoss(@Param("name") List<String> name);

    // RAG의 검색 후 유사도가 낮을경우 몬스터 랜덤출현 (일반)
    @Query("SELECT m FROM MonsterEntity m WHERE m.monsterType != 2 ORDER BY function('RANDOM')")
    List<MonsterEntity> findRandomMonsterNormal(Pageable pageable);

    // RAG의 검색 후 유사도가 낮을경우 몬스터 랜덤출현 (보스)
    @Query("SELECT m FROM MonsterEntity m WHERE m.monsterType = 2 ORDER BY function('RANDOM')")
    List<MonsterEntity> findRandomMonsterBoss(Pageable pageable);

    // 타입별 몬스터 선택 (normal / elite / boss)
}
