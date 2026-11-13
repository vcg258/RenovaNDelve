package com.renovandelve.dto.response;

import com.renovandelve.domain.gamestateentity.GameStateEntity;

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
    public static GameHistoryDTO from(GameStateEntity game) {
        boolean active = Boolean.TRUE.equals(game.getIsActive());
        return new GameHistoryDTO(
                game.getGameStateId(),
                game.getDifficulty().name(),
                game.getCurrentStage(),
                game.getCurrentNodeId() == null ? 0 : game.getCurrentNodeId(),
                game.getScore(),
                active ? "PLAYING" : game.getResult(),
                game.getCreatedAt(),
                active ? null : game.getUpdatedAt());
    }
}
