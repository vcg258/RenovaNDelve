package com.renovandelve.repository;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.RankingEntity;
import com.renovandelve.domain.gamestateentity.Difficulty;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface RankingRepository extends JpaRepository<RankingEntity, Long> {
    // 난이도별 TOP 랭킹
    List<RankingEntity> findTop10ByDifficultyOrderByBestScoreDesc(Difficulty difficulty);

    // 특정 유저 기록 조회
    Optional<RankingEntity> findByMemberMemberIdAndDifficulty(Long memberId, Difficulty difficulty);

    Optional<RankingEntity> findByMemberAndDifficulty(MemberEntity member, Difficulty difficulty);
}
