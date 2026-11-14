package com.renovandelve.game.dto.response;

import com.renovandelve.game.engine.RunStats;

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
    /**
     * 세션의 통계를 응답용으로 옮긴다.
     *
     * @param stats 세션 통계 (없으면 모두 0)
     * @return 응답용 통계
     */
    public static RunStatsDTO from(RunStats stats) {
        RunStats s = stats == null ? new RunStats() : stats;
        return new RunStatsDTO(s.getTurns(), s.getBattlesWon(), s.getBattlesFled(), s.getMonstersKilled(),
                s.getElitesKilled(), s.getBossesKilled(), s.getDamageDealt(), s.getDamageTaken(),
                s.getBiggestBattle(), s.getEncounters(), s.getConsumablesUsed(), s.getGoldFromBattles());
    }
}
