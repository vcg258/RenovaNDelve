package com.renovandelve.repository;

import com.renovandelve.domain.GameRelicInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameRelicInventoryRepository extends JpaRepository<GameRelicInventoryEntity, Long> {
    // 현재 가지고 있는 유물 조회
    List<GameRelicInventoryEntity> findByGameState_GameStateId(Long gameStateId);
}
