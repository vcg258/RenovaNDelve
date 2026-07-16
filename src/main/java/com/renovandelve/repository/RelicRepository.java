package com.renovandelve.repository;

import com.renovandelve.domain.relicentity.RelicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface RelicRepository extends JpaRepository<RelicEntity, Long> {
    // 유물 단건 조회
    Optional<RelicEntity> findByNameContains(String name);

    // 보유하지 않는 유물 조회
    @Query("""
            SELECT r FROM RelicEntity r
            WHERE r.relicId NOT IN (
                SELECT ri.relic.relicId FROM GameRelicInventoryEntity ri
                WHERE ri.gameState.gameStateId = :gameStateId
            )
            """)
    List<RelicEntity> findByRelicNotOwner(@Param("gameStateId") Long gameStateId);
}
