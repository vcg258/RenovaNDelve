package com.renovandelve.dto.response;

/**
 * 게임 종료(클리어 / 게임오버) 요약.
 * narrative = AI가 요약한 이번 모험의 서사
 */
public record GameSummaryDTO(
        boolean cleared,
        int score,
        int gold,
        int stage,
        long node,
        int level,
        String narrative
) {
}
