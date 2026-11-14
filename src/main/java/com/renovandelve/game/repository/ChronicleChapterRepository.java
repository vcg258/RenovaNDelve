package com.renovandelve.game.repository;

import com.renovandelve.game.domain.ChronicleChapterEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ChronicleChapterRepository extends JpaRepository<ChronicleChapterEntity, Long> {

    /**
     * 회원의 연대기 전체, 1장부터.
     *
     * @param memberId 회원 ID
     * @return 장 목록
     */
    List<ChronicleChapterEntity> findByMemberIdOrderByChapterNoAsc(Long memberId);

    /**
     * 가장 최근 장 몇 개 (다음 장을 이어 쓸 때 AI에게 주는 앞 이야기).
     *
     * @param memberId 회원 ID
     * @return 최근 3장, 최신 순
     */
    List<ChronicleChapterEntity> findTop3ByMemberIdOrderByChapterNoDesc(Long memberId);

    /**
     * 이 원정의 장이 이미 있는지.
     *
     * @param gameStateId 게임 ID
     * @return 있으면 true
     */
    boolean existsByGameStateId(Long gameStateId);

    /**
     * 회원의 장 수 (다음 장 번호).
     *
     * @param memberId 회원 ID
     * @return 장 수
     */
    long countByMemberId(Long memberId);

    /**
     * 회원의 연대기를 모두 지운다 (탈퇴).
     *
     * @param memberId 회원 ID
     * @return 지운 행 수
     */
    @Modifying
    @Query("delete from ChronicleChapterEntity c where c.memberId = :memberId")
    int deleteAllByMemberId(@Param("memberId") Long memberId);
}
