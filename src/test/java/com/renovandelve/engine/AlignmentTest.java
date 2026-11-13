package com.renovandelve.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

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

    /* ==================== 단계별 가호 ==================== */

    @Test
    @DisplayName("가호 단계는 단독 1위 세력의 점수가 3 / 6 / 9에 닿을 때마다 오른다")
    void tierRisesWithScore() {
        assertThat(new Alignment(2, 0, 0).tier()).isZero();
        assertThat(new Alignment(3, 0, 0).tier()).isEqualTo(1);
        assertThat(new Alignment(5, 0, 0).tier()).isEqualTo(1);
        assertThat(new Alignment(6, 0, 0).tier()).isEqualTo(2);
        assertThat(new Alignment(9, 0, 0).tier()).isEqualTo(3);
        assertThat(new Alignment(20, 0, 0).tier()).isEqualTo(3); // 3단계가 끝
    }

    @Test
    @DisplayName("점수가 높아도 동률이면 단계가 없다")
    void tieHasNoTierEvenWithHighScore() {
        Alignment alignment = new Alignment(9, 9, 0);

        assertThat(alignment.tier()).isZero();
        assertThat(alignment.nextTierAt()).isNull();
        assertThat(alignment.bonusRelicOptions()).isZero();
    }

    @Test
    @DisplayName("1단계 수치는 단계가 없던 때와 같다 (기존 밸런스 유지)")
    void firstTierKeepsOriginalNumbers() {
        assertThat(new Alignment(3, 0, 0).victoryHealRatio()).isCloseTo(0.06, within(1e-9));
        assertThat(new Alignment(0, 3, 0).attackMultiplier()).isCloseTo(1.12, within(1e-9));
        assertThat(new Alignment(0, 3, 0).damageTakenMultiplier()).isCloseTo(1.10, within(1e-9));
        assertThat(new Alignment(0, 0, 3).goldMultiplier()).isCloseTo(1.20, within(1e-9));
    }

    @Test
    @DisplayName("단계가 오를수록 가호가 강해진다")
    void higherTierIsStronger() {
        assertThat(new Alignment(6, 0, 0).victoryHealRatio()).isGreaterThan(new Alignment(3, 0, 0).victoryHealRatio());
        assertThat(new Alignment(9, 0, 0).victoryHealRatio()).isGreaterThan(new Alignment(6, 0, 0).victoryHealRatio());
        assertThat(new Alignment(0, 9, 1).attackMultiplier()).isGreaterThan(new Alignment(0, 6, 1).attackMultiplier());
        assertThat(new Alignment(1, 0, 9).goldMultiplier()).isGreaterThan(new Alignment(1, 0, 6).goldMultiplier());
    }

    @Test
    @DisplayName("3단계에서만 보상 후보가 늘고, 세력마다 늘어나는 종류가 다르다")
    void thirdTierAddsRewardOption() {
        Alignment heaven = new Alignment(9, 0, 0);
        Alignment abyss = new Alignment(0, 9, 0);
        Alignment human = new Alignment(0, 0, 9);

        assertThat(heaven.bonusConsumableOptions()).isEqualTo(1);
        assertThat(heaven.bonusRelicOptions()).isZero();
        assertThat(abyss.bonusRelicOptions()).isEqualTo(1);
        assertThat(abyss.bonusSkillOptions()).isZero();
        assertThat(human.bonusSkillOptions()).isEqualTo(1);
        assertThat(human.bonusConsumableOptions()).isZero();

        assertThat(new Alignment(8, 0, 0).bonusConsumableOptions()).isZero(); // 2단계까지는 없다
    }

    @Test
    @DisplayName("다음 단계에 필요한 점수와 가호 설명에 단계가 드러난다")
    void nextTierAndDescription() {
        assertThat(new Alignment(1, 0, 0).nextTierAt()).isNull();
        assertThat(new Alignment(3, 0, 0).nextTierAt()).isEqualTo(6);
        assertThat(new Alignment(7, 0, 0).nextTierAt()).isEqualTo(9);
        assertThat(new Alignment(9, 0, 0).nextTierAt()).isNull();

        assertThat(new Alignment(0, 6, 0).blessing()).contains("2단계").contains("+18%");
        assertThat(new Alignment(0, 0, 9).blessing()).contains("3단계").contains("스킬 후보 +1");
    }
}
