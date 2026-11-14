package com.renovandelve.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 한 원정의 누적 통계 (원정이 끝난 뒤 결과 화면에 보여 준다).
 *
 * <p>게임 세션(Redis)에 함께 저장되므로 DB 스키마를 바꾸지 않는다. 세션이 만료되면(24시간) 통계도 사라진다.
 * 로그라이크의 "죽음 화면 통계"에 해당하며, 플레이어가 이번 판을 돌아보고 다음 판의 전략을 세우는 재료가 된다.</p>
 */
@Data
@NoArgsConstructor
public class RunStats {
    private int turns;            // 전투에서 보낸 턴 수
    private int battlesWon;       // 이긴 전투
    private int battlesFled;      // 도망친 전투
    private int monstersKilled;   // 쓰러뜨린 적 (보스·강적 포함)
    private int elitesKilled;     // 쓰러뜨린 강적
    private int bossesKilled;     // 쓰러뜨린 보스
    private long damageDealt;     // 적에게 준 피해 합 (넘치는 피해는 세지 않는다)
    private long damageTaken;     // 받은 피해 합
    private int biggestBattle;    // 한 전투에서 적에게 준 피해가 가장 컸던 값
    private int encounters;       // 조우에서 선택한 횟수
    private int consumablesUsed;  // 쓴 소모품
    private long goldFromBattles; // 전투 승리로 얻은 골드

    /**
     * 전투 한 턴이 지났다.
     */
    public void recordTurn() {
        turns++;
    }

    /**
     * 플레이어가 받은 피해를 더한다.
     *
     * @param amount 피해량 (0 이하는 무시)
     */
    public void recordDamageTaken(int amount) {
        if (amount > 0) damageTaken += amount;
    }

    /**
     * 전투가 끝났을 때 적에게 준 피해와 쓰러뜨린 적을 센다.
     * 각 적이 잃은 HP(최대 HP - 남은 HP)를 더하므로 넘치는 피해는 세지 않는다.
     *
     * @param monsters 이번 전투의 적
     * @param won      이겼으면 true, 도망치거나 쓰러졌으면 false
     */
    public void recordBattleEnd(List<SessionMonster> monsters, boolean won) {
        int dealt = 0;
        for (SessionMonster monster : monsters) {
            dealt += Math.max(0, monster.getMaxHp() - monster.getCurrentHp());
            if (!monster.isAlive()) {
                monstersKilled++;
                if (monster.getMonsterType() == 2) bossesKilled++;
                else if (monster.getMonsterType() == 1) elitesKilled++;
            }
        }
        damageDealt += dealt;
        biggestBattle = Math.max(biggestBattle, dealt);
        if (won) battlesWon++;
    }

    /**
     * 전투에서 도망쳤다. 그때까지 준 피해와 쓰러뜨린 적은 그대로 센다.
     *
     * @param monsters 이번 전투의 적
     */
    public void recordFlee(List<SessionMonster> monsters) {
        recordBattleEnd(monsters, false);
        battlesFled++;
    }

    /**
     * 조우에서 선택지를 하나 골랐다.
     */
    public void recordEncounter() {
        encounters++;
    }

    /**
     * 소모품을 하나 썼다.
     */
    public void recordConsumable() {
        consumablesUsed++;
    }

    /**
     * 전투 승리로 얻은 골드를 더한다.
     *
     * @param gold 얻은 골드
     */
    public void recordGold(int gold) {
        if (gold > 0) goldFromBattles += gold;
    }
}
