package com.renovandelve.repository;

import com.renovandelve.domain.GameCharacterEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameCharacterRepository extends JpaRepository<GameCharacterEntity, Long> {
    // 캐릭터 정보 조회
    Optional<GameCharacterEntity> findByGameState_GameStateId(Long gameStateId);
}
