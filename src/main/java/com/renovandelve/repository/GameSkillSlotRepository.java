package com.renovandelve.repository;

import com.renovandelve.domain.GameSkillSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GameSkillSlotRepository extends JpaRepository<GameSkillSlotEntity, Long> {
    // 현재 장착 중인 스킬 조회
    List<GameSkillSlotEntity> findByGameState_GameStateIdOrderBySlotIndexAsc(Long gameStateId);

    // 스킬 하나 조회
    Optional<GameSkillSlotEntity> findByGameState_GameStateIdAndSlotIndex(Long gameStateId, int slotIndex);
}
