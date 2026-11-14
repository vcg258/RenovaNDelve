package com.renovandelve.game.repository;

import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface GameStateRepository extends JpaRepository<GameStateEntity, Long> {
    // 진행 중인 게임 조회
    Optional<GameStateEntity> findByMember_MemberIdAndIsActiveTrue(Long memberId);

    // 모험 기록 (최신순)
    List<GameStateEntity> findByMember_MemberIdOrderByCreatedAtDesc(Long memberId);

    // 게임 이어하기
    Optional<GameStateEntity> findTopByMember_MemberIdOrderByCreatedAtDesc(Long memberId);

    // 관리자: 현재 진행 중인 전체 게임 (회원까지 함께 조회하여 N+1 방지)
    @Query("SELECT g FROM GameStateEntity g JOIN FETCH g.member WHERE g.isActive = true")
    List<GameStateEntity> findByIsActiveTrue();
}
