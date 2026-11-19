package com.renovandelve.game.dto.response;

import java.util.List;

/**
 * 전투 1턴 처리 결과.
 * result = WIN / LOSE / CONTINUE / FLED(도망쳐서 전투가 끝남, 보상 없음)
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

    /** 전투가 이어진다 */
    public static TurnResultDTO ongoing(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status, String phase) {
        return new TurnResultDTO("CONTINUE", log, monsters, status, null, false, phase);
    }

    /** 이겼다. 전리품이 있으면 reward에 싣는다 (없으면 null) */
    public static TurnResultDTO won(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status,
                                    RewardViewDTO reward, String phase) {
        return new TurnResultDTO("WIN", log, monsters, status, reward, false, phase);
    }

    /** 마지막 보스를 쓰러뜨려 원정을 끝냈다 */
    public static TurnResultDTO cleared(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status, String phase) {
        return new TurnResultDTO("WIN", log, monsters, status, null, true, phase);
    }

    /** 쓰러졌다 */
    public static TurnResultDTO lost(List<String> log, List<BattleMonsterDTO> monsters, StatusDTO status, String phase) {
        return new TurnResultDTO("LOSE", log, monsters, status, null, false, phase);
    }

    /** 달아났다. 전투가 끝났으므로 몬스터는 비운다 */
    public static TurnResultDTO fled(List<String> log, StatusDTO status, String phase) {
        return new TurnResultDTO("FLED", log, List.of(), status, null, false, phase);
    }
}
