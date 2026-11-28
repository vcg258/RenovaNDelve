package com.renovandelve.game.repository;

import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /**
     * 회원별 성향 합계 (왕국의 저울). 기준 시각 이후에 시작한 원정만 센다.
     *
     * @param since 기준 시각
     * @return [회원 ID, 천상 합, 마계 합, 인간 합] 목록
     */
    @Query("SELECT g.member.memberId, SUM(g.heavenAffinity), SUM(g.abyssAffinity), SUM(g.humanAffinity) "
            + "FROM GameStateEntity g WHERE g.createdAt >= :since GROUP BY g.member.memberId")
    List<Object[]> sumAffinityByMemberSince(@Param("since") java.time.LocalDateTime since);

    // 원정 준비의 목표: 이 회원이 클리어한 적 있는 난이도
    @Query("SELECT DISTINCT g.difficulty FROM GameStateEntity g WHERE g.member.memberId = :memberId AND g.result = 'CLEAR'")
    Set<Difficulty> findClearedDifficulties(@Param("memberId") Long memberId);
}
