package com.renovandelve.repository;

import com.renovandelve.domain.gamestateentity.GameStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface GameStateRepository extends JpaRepository<GameStateEntity, Long> {
    // 진행 중인 게임 조회
    Optional<GameStateEntity> findByMember_MemberIdAndIsActiveTrue(Long memberId);

    // 게임 이어하기
    Optional<GameStateEntity> findTopByMember_MemberIdOrderByCreatedAtDesc(Long memberId);
}
