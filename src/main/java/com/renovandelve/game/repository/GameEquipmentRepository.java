package com.renovandelve.game.repository;

import com.renovandelve.game.domain.GameEquipmentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameEquipmentRepository extends JpaRepository<GameEquipmentEntity, Long> {
    // 현재 장착중인 장비 조회
    List<GameEquipmentEntity> findByGameState_GameStateId(Long gameStateId);
}
