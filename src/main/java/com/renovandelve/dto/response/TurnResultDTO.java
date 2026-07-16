package com.renovandelve.dto.response;

import java.util.List;

/**
 * 전투 1턴 처리 결과.
 * result = WIN / LOSE / CONTINUE
 * phase = 턴 종료 후 게임 단계 (BATTLE 유지, WIN 시 REWARD 또는 CLEAR, LOSE 시 GAMEOVER)
 */
public record TurnResultDTO(
        String result,
        List<String> log,
        List<BattleMonsterDTO> monsters,
        StatusDTO status,
        RewardViewDTO reward,
        boolean cleared,
        String phase
) {
}
