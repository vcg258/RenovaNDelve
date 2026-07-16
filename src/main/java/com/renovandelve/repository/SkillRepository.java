package com.renovandelve.repository;

import com.renovandelve.domain.skillentity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface SkillRepository extends JpaRepository<SkillEntity, Long> {
    Optional<SkillEntity> findByName(String name);

    // 보유하지 않는 스킬 조회
    @Query("""
            SELECT s FROM SkillEntity s
            WHERE s.skillId NOT IN (
                SELECT gs.skill.skillId FROM GameSkillSlotEntity gs
                WHERE gs.gameState.gameStateId = :gameStateId
            )
            """)
    List<SkillEntity> findBySkillNotOwner(@Param("gameStateId") Long gameStateId);
}
