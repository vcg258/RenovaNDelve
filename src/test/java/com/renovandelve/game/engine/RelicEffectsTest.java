package com.renovandelve.game.engine;

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
        // 사냥꾼의 눈 10% + 사신의 공명 5% + 매의 호흡 5% (사냥꾼의 눈 + 고대의 부적 조합이 추가됐다)
        assertThat(four.getCritBonus()).isCloseTo(0.20, within(1e-9));
    }

    /* ==================== 추가된 조합 ==================== */

    @Test
    @DisplayName("매의 호흡: 사냥꾼의 눈 + 고대의 부적 -> 치명타 +5% 추가")
    void hawkBreathSynergy() {
        RelicEffects effects = RelicEffects.of(List.of("사냥꾼의 눈", "고대의 부적"));

        assertThat(effects.getCritBonus()).isCloseTo(0.15, within(1e-9));
        assertThat(effects.getActiveSynergies()).hasSize(1).first().asString().startsWith("매의 호흡");
    }

    @Test
    @DisplayName("피의 갈증: 핏빛 반지 + 심연의 핵 -> 출혈 피해 +25% 추가")
    void bloodThirstSynergy() {
        RelicEffects effects = RelicEffects.of(List.of("핏빛 반지", "심연의 핵"));

        assertThat(effects.getBleedMultiplier()).isCloseTo(1.75, within(1e-9));
    }

    @Test
    @DisplayName("수호의 맥박: 수호자의 문장 + 고대의 부적 -> 방어력 +2")
    void guardianPulseSynergy() {
        RelicEffects effects = RelicEffects.of(List.of("수호자의 문장", "고대의 부적"));

        assertThat(effects.getDefenseBonus()).isEqualTo(2);
        assertThat(effects.getFlatDamageReduction()).isEqualTo(2); // 단일 효과는 그대로
    }

    @Test
    @DisplayName("황금 사냥: 행운의 반지 + 사냥꾼의 눈 -> 골드 +10% 추가")
    void goldHuntSynergy() {
        RelicEffects effects = RelicEffects.of(List.of("행운의 반지", "사냥꾼의 눈"));

        assertThat(effects.getGoldMultiplier()).isCloseTo(1.35, within(1e-9));
    }

    @Test
    @DisplayName("시너지 문구에는 이모지가 없다 (화면은 도트 아이콘만 쓴다)")
    void synergyTextHasNoEmoji() {
        RelicEffects effects = RelicEffects.of(List.of("행운의 반지", "사냥꾼의 눈", "핏빛 반지", "마력 증폭 수정",
                "수호자의 문장", "고대의 부적", "심연의 핵"));

        assertThat(effects.getActiveSynergies()).hasSize(9);
        assertThat(effects.getActiveSynergies()).allSatisfy(text ->
                assertThat(text.codePoints().noneMatch(cp -> cp > 0xFFFF || (cp >= 0x2600 && cp <= 0x27BF))).isTrue());
    }

    @Test
    @DisplayName("v0.12.0 유물: 승리 회복, 경험치, 상점 할인, 공격·방어·치명타")
    void newRelics() {
        RelicEffects effects = RelicEffects.of(List.of("순례자의 묵주", "견습 학자의 수정", "수비대의 문장", "상인의 반지",
                "결의의 반지", "천사의 부적", "마왕의 눈", "왕관의 파편"));
        assertThat(effects.getVictoryHeal()).isEqualTo(6 + 12 + 6); // 순례의 끝 시너지 +6
        assertThat(effects.getExpMultiplier()).isCloseTo(1.45, within(1e-9));
        assertThat(effects.getShopDiscount()).isCloseTo(0.15, within(1e-9));
        assertThat(effects.getDefenseBonus()).isEqualTo(2);
        assertThat(effects.getFlatDamageReduction()).isEqualTo(1);
        assertThat(effects.getCritBleedStacks()).isEqualTo(1);
        assertThat(effects.getActiveSynergies()).anyMatch(t -> t.startsWith("순례의 끝 — "));
    }

    @Test
    @DisplayName("상점 할인은 시너지를 더해도 50%를 넘지 않는다")
    void shopDiscountIsCapped() {
        RelicEffects effects = RelicEffects.of(List.of("상인의 반지", "행운의 반지"));
        assertThat(effects.getShopDiscount()).isCloseTo(0.25, within(1e-9));
        assertThat(effects.getActiveSynergies()).anyMatch(t -> t.startsWith("흥정의 달인 — "));
        assertThat(RelicEffects.MAX_SHOP_DISCOUNT).isEqualTo(0.5);
    }

    @Test
    @DisplayName("마왕의 눈 + 핏빛 반지: 피의 군주")
    void bloodLordSynergy() {
        RelicEffects effects = RelicEffects.of(List.of("마왕의 눈", "핏빛 반지"));
        assertThat(effects.getBleedMultiplier()).isCloseTo(1.75, within(1e-9));
    }
}
