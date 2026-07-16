package com.renovandelve.dto.response;

import com.renovandelve.domain.gamestateentity.Difficulty;

import java.time.LocalDateTime;

/**
 * 랭킹 보드 한 줄.
 */
public record RankingViewDTO(
        int rank,
        String loginId,
        Difficulty difficulty,
        int bestScore,
        Integer clearTime,
        LocalDateTime achievedAt
) {
}
