package com.renovandelve.game.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 원정 통계 규칙: 넘치는 피해는 세지 않고, 등급별로 쓰러뜨린 적을 나눠 센다.
 */
class RunStatsTest {

    private SessionMonster monster(int maxHp, int currentHp, int type) {
        SessionMonster monster = new SessionMonster();
        monster.setMaxHp(maxHp);
        monster.setCurrentHp(currentHp);
        monster.setMonsterType((short) type);
        return monster;
    }

    @Test
    @DisplayName("이긴 전투: 적이 잃은 HP만큼 준 피해로 세고, 등급별 처치 수를 나눠 센다")
    void countsVictory() {
        RunStats stats = new RunStats();

        stats.recordBattleEnd(List.of(monster(50, 0, 0), monster(120, 0, 1)), true);
        stats.recordBattleEnd(List.of(monster(300, 0, 2)), true);

        assertThat(stats.getBattlesWon()).isEqualTo(2);
        assertThat(stats.getMonstersKilled()).isEqualTo(3);
        assertThat(stats.getElitesKilled()).isEqualTo(1);
        assertThat(stats.getBossesKilled()).isEqualTo(1);
        assertThat(stats.getDamageDealt()).isEqualTo(470);
        assertThat(stats.getBiggestBattle()).isEqualTo(300);
    }

    @Test
    @DisplayName("도망친 전투: 그때까지 준 피해와 쓰러뜨린 적은 세지만 승리로는 세지 않는다")
    void countsFlee() {
        RunStats stats = new RunStats();

        stats.recordFlee(List.of(monster(60, 0, 0), monster(80, 50, 1)));

        assertThat(stats.getBattlesFled()).isEqualTo(1);
        assertThat(stats.getBattlesWon()).isZero();
        assertThat(stats.getMonstersKilled()).isEqualTo(1);
        assertThat(stats.getElitesKilled()).isZero();
        assertThat(stats.getDamageDealt()).isEqualTo(90);
    }

    @Test
    @DisplayName("받은 피해·골드는 음수나 0을 더하지 않는다")
    void ignoresNonPositive() {
        RunStats stats = new RunStats();

        stats.recordDamageTaken(0);
        stats.recordDamageTaken(-5);
        stats.recordDamageTaken(12);
        stats.recordGold(-3);
        stats.recordGold(40);

        assertThat(stats.getDamageTaken()).isEqualTo(12);
        assertThat(stats.getGoldFromBattles()).isEqualTo(40);
    }
}
