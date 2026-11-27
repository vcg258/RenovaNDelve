package com.renovandelve.game.dto.response;

/**
 * 원정 통계 (결과 화면용).
 */
public record RunStatsDTO(
        int turns,
        int battlesWon,
        int battlesFled,
        int monstersKilled,
        int elitesKilled,
        int bossesKilled,
        long damageDealt,
        long damageTaken,
        int biggestBattle,
        int encounters,
        int consumablesUsed,
        long goldFromBattles
) {
}
