package com.renovandelve.game.dto.response;

import java.time.LocalDateTime;

/**
 * 모험 기록 한 건.
 * result = PLAYING(진행 중) / CLEAR / DEFEAT / ABANDON / null(결과 컬럼 추가 이전의 과거 기록)
 */
public record GameHistoryDTO(
        Long gameStateId,
        String difficulty,
        int stage,
        long node,
        int score,
        String result,
        LocalDateTime startedAt,
        LocalDateTime endedAt
) {
}
