package com.renovandelve.member.dto.response;

import com.renovandelve.ranking.dto.response.RankingViewDTO;

import java.util.List;
import java.util.Map;

/**
 * 관리자 대시보드 통계.
 */
public record AdminStatsDTO(
        long totalMembers,
        long activeMembers,
        long suspendedMembers,
        long withdrawnMembers,
        long newMembersLast7Days,
        long activeGames,
        long totalGamesPlayed,
        long clearedGames,
        Map<String, Long> gamesByDifficulty,
        List<RankingViewDTO> topRankings
) {
}
