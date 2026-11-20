package com.renovandelve.game.repository;

import com.renovandelve.game.domain.GameRelicInventoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;


public interface GameRelicInventoryRepository extends JpaRepository<GameRelicInventoryEntity, Long> {
    // 현재 가지고 있는 유물 조회
}
