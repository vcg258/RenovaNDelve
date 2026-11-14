package com.renovandelve.game.repository;

import com.renovandelve.game.domain.GameConsumableInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameConsumableInventoryRepository extends JpaRepository<GameConsumableInventoryEntity, Long> {
    // 현재 가지고 있는 소모품 조회
    List<GameConsumableInventoryEntity> findByGameState_GameStateId(Long gameStateId);
}
