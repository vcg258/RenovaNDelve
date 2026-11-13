package com.renovandelve.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RelicEffectsTest {

    @Test
    @DisplayName("유물이 없으면 모든 배율이 기본값이다")
    void noRelics() {
        RelicEffects effects = RelicEffects.of(List.of());

        assertThat(effects.getGoldMultiplier()).isEqualTo(1.0);
        assertThat(effects.getAttackMultiplier()).isEqualTo(1.0);
        assertThat(effects.getFlatDamageReduction()).isZero();
        assertThat(effects.getActiveSynergies()).isEmpty();
    }

    @Test
    @DisplayName("단일 유물 효과")
    void singleRelic() {
        RelicEffects effects = RelicEffects.of(List.of("행운의 반지"));

        assertThat(effects.getGoldMultiplier()).isCloseTo(1.25, within(1e-9));
        assertThat(effects.getActiveSynergies()).isEmpty();
    }

    @Test
    @DisplayName("피의 사냥: 핏빛 반지 + 사냥꾼의 눈 -> 치명타 시 출혈 2스택")
    void bloodHuntSynergy() {
        RelicEffects effects = RelicEffects.of(List.of("핏빛 반지", "사냥꾼의 눈"));

        assertThat(effects.getCritBleedStacks()).isEqualTo(2);
        assertThat(effects.getBleedMultiplier()).isCloseTo(1.5, within(1e-9));
        assertThat(effects.getCritBonus()).isCloseTo(0.10, within(1e-9));
        assertThat(effects.getActiveSynergies()).hasSize(1);
    }

    @Test
    @DisplayName("심연의 핵은 두 시너지에 동시에 참여한다")
    void abyssCoreJoinsTwoSynergies() {
        RelicEffects effects = RelicEffects.of(List.of("행운의 반지", "심연의 핵", "수호자의 문장"));

        assertThat(effects.getGoldMultiplier()).isCloseTo(1.5, within(1e-9)); // 0.25 + 시너지 0.25
        assertThat(effects.getFlatDamageReduction()).isEqualTo(4);            // 2 + 시너지 2
        assertThat(effects.getActiveSynergies()).hasSize(2);
    }

    @Test
    @DisplayName("같은 유물 이름이 중복으로 들어와도 효과는 한 번만 적용된다")
    void duplicatesIgnored() {
        RelicEffects effects = RelicEffects.of(List.of("행운의 반지", "행운의 반지"));

        assertThat(effects.getGoldMultiplier()).isCloseTo(1.25, within(1e-9));
    }

    @Test
    @DisplayName("유물 4개 이상이면 사신의 공명 발동")
    void resonanceWithFourRelics() {
        RelicEffects three = RelicEffects.of(List.of("행운의 반지", "사냥꾼의 눈", "고대의 부적"));
        RelicEffects four = RelicEffects.of(List.of("행운의 반지", "사냥꾼의 눈", "고대의 부적", "수호자의 문장"));

        assertThat(three.getAttackMultiplier()).isEqualTo(1.0);
        assertThat(four.getAttackMultiplier()).isCloseTo(1.05, within(1e-9));
        assertThat(four.getCritBonus()).isCloseTo(0.15, within(1e-9));
    }
}
