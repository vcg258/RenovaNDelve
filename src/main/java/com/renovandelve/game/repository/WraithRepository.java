package com.renovandelve.game.repository;

import com.renovandelve.game.domain.WraithEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface WraithRepository extends JpaRepository<WraithEntity, Long> {

    /**
     * 회원의 망령을 상태로 찾는다.
     *
     * @param memberId 회원 ID
     * @param status   상태
     * @return 망령 목록
     */
    List<WraithEntity> findByMemberIdAndStatus(Long memberId, WraithEntity.Status status);

    /**
     * 회원이 지금 만날 수 있는 망령 (가장 최근 것).
     *
     * @param memberId 회원 ID
     * @param status   ACTIVE
     * @return 망령
     */
    Optional<WraithEntity> findFirstByMemberIdAndStatusOrderByCreatedAtDesc(Long memberId, WraithEntity.Status status);

    /**
     * 회원의 망령 기록 전체 (내 정보 내려받기용).
     *
     * @param memberId 회원 ID
     * @return 망령 목록, 최근 순
     */
    List<WraithEntity> findByMemberIdOrderByCreatedAtDesc(Long memberId);

    /**
     * 회원의 망령 기록을 모두 지운다 (탈퇴).
     *
     * @param memberId 회원 ID
     * @return 지운 행 수
     */
    @Modifying
    @Query("delete from WraithEntity w where w.memberId = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);

    /**
     * 보관 기간이 지난 끝난 망령(SLAIN / FADED)을 지운다. 아직 나타날 망령(ACTIVE)은 남긴다.
     *
     * @param threshold 이 시각 전에 마지막으로 바뀐 기록을 지운다
     * @return 지운 행 수
     */
    @Transactional
    @Modifying
    @Query("delete from WraithEntity w where w.status <> com.renovandelve.game.domain.WraithEntity.Status.ACTIVE "
            + "and w.updatedAt < :threshold")
    int deleteFinishedBefore(@Param("threshold") LocalDateTime threshold);
}
