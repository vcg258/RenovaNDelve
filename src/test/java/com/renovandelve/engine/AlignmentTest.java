package com.renovandelve.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AlignmentTest {

    @Test
    @DisplayName("임계치(3) 미만이면 어느 세력의 가호도 없다")
    void noBlessingBelowThreshold() {
        Alignment alignment = new Alignment(2, 1, 0);

        assertThat(alignment.dominant()).isNull();
        assertThat(alignment.blessing()).isNull();
        assertThat(alignment.attackMultiplier()).isEqualTo(1.0);
        assertThat(alignment.goldMultiplier()).isEqualTo(1.0);
        assertThat(alignment.victoryHealRatio()).isZero();
    }

    @Test
    @DisplayName("동률이면 가호가 없다 - 골고루 고르는 플레이는 보상받지 못한다")
    void tieGivesNoBlessing() {
        assertThat(new Alignment(3, 3, 0).dominant()).isNull();
        assertThat(new Alignment(4, 4, 4).dominant()).isNull();
    }

    @Test
    @DisplayName("천상: 승리 시 회복만 있고 다른 배율은 그대로")
    void heavenBlessing() {
        Alignment alignment = new Alignment(3, 1, 2);

        assertThat(alignment.dominant()).isEqualTo(Faction.HEAVEN);
        assertThat(alignment.victoryHealRatio()).isGreaterThan(0);
        assertThat(alignment.attackMultiplier()).isEqualTo(1.0);
        assertThat(alignment.damageTakenMultiplier()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("마계: 공격력이 오르는 대신 받는 피해도 오른다")
    void abyssBlessingHasPrice() {
        Alignment alignment = new Alignment(0, 5, 2);

        assertThat(alignment.dominant()).isEqualTo(Faction.ABYSS);
        assertThat(alignment.attackMultiplier()).isGreaterThan(1.0);
        assertThat(alignment.damageTakenMultiplier()).isGreaterThan(1.0);
        assertThat(alignment.goldMultiplier()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("인간: 골드 획득만 오른다")
    void humanBlessing() {
        Alignment alignment = new Alignment(1, 0, 3);

        assertThat(alignment.dominant()).isEqualTo(Faction.HUMAN);
        assertThat(alignment.goldMultiplier()).isGreaterThan(1.0);
        assertThat(alignment.damageTakenMultiplier()).isEqualTo(1.0);
    }
}
