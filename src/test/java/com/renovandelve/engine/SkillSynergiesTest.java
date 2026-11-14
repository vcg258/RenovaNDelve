package com.renovandelve.engine;

import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.skillentity.Target;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 스킬 조합 시너지. 스킬 이름이 아니라 성질(상태이상 / 대상 / 계수)로 판정하는지 확인한다.
 */
class SkillSynergiesTest {

    private SkillEntity skill(String name, Target target, double coefficient, int statusEffect) {
        return SkillEntity.builder().name(name).target(target).attackCount(1)
                .coefficient(BigDecimal.valueOf(coefficient)).statusEffect((short) statusEffect).build();
    }

    private SkillEntity bleed(String name) { return skill(name, Target.EnemySingle, 0.7, 1); }
    private SkillEntity burn(String name) { return skill(name, Target.EnemyAll, 1.1, 2); }
    private SkillEntity poison(String name) { return skill(name, Target.EnemySingle, 0.9, 3); }
    private SkillEntity stun(String name) { return skill(name, Target.EnemySingle, 0.7, 4); }
    private SkillEntity plain(String name, Target target, double coefficient) { return skill(name, target, coefficient, 0); }

    private SessionMonster monster(String intent) {
        SessionMonster monster = new SessionMonster();
        monster.setIntent(intent);
        return monster;
    }

    @Test
    @DisplayName("조합이 없으면 모든 배율이 기본값이고 추가 스택도 없다")
    void noSynergy() {
        SkillSynergies synergies = SkillSynergies.of(List.of(plain("기본 공격", Target.EnemySingle, 1.0), bleed("출혈 베기")));

        assertThat(synergies.getActiveSynergies()).isEmpty();
        assertThat(synergies.getBleedMultiplierBonus()).isZero();
        assertThat(synergies.extraStacks(SkillSynergies.BURN)).isZero();
        assertThat(synergies.targetDamageMultiplier(monster("HEAVY"))).isEqualTo(1.0);
        assertThat(SkillSynergies.of(null).getActiveSynergies()).isEmpty();
    }

    @Test
    @DisplayName("피의 연계: 출혈 스킬 2개 -> 출혈 피해 +50%")
    void bleedChain() {
        SkillSynergies synergies = SkillSynergies.of(List.of(bleed("연속 베기"), bleed("출혈 베기")));

        assertThat(synergies.getBleedMultiplierBonus()).isCloseTo(0.5, within(1e-9));
        assertThat(synergies.getActiveSynergies()).hasSize(1).first().asString().startsWith("피의 연계");
    }

    @Test
    @DisplayName("번지는 불길: 화상 스킬 2개 -> 화상 +1스택 / 전체 공격 2개이므로 휩쓸기도 함께 발동")
    void burnAndSweep() {
        SkillEntity fireball = burn("화염구");
        SkillSynergies synergies = SkillSynergies.of(List.of(fireball, burn("헬파이어")));

        assertThat(synergies.extraStacks(SkillSynergies.BURN)).isEqualTo(1);
        assertThat(synergies.extraStacks(SkillSynergies.POISON)).isZero();
        assertThat(synergies.skillDamageMultiplier(fireball)).isCloseTo(1.10, within(1e-9));
        assertThat(synergies.skillDamageMultiplier(bleed("단일 대상"))).isEqualTo(1.0); // 단일 대상 스킬에는 붙지 않는다
        assertThat(synergies.getActiveSynergies()).hasSize(2);
    }

    @Test
    @DisplayName("상처에 스민 독: 독 스킬 + 출혈 스킬 -> 독 +1스택")
    void poisonedWound() {
        SkillSynergies synergies = SkillSynergies.of(List.of(poison("맹독 단검"), bleed("출혈 베기")));

        assertThat(synergies.extraStacks(SkillSynergies.POISON)).isEqualTo(1);
        assertThat(synergies.extraStacks(SkillSynergies.BLEED)).isZero();
    }

    @Test
    @DisplayName("빈틈 찌르기: 기절 스킬 + 고계수 스킬 -> 강공을 준비 중인 적에게만 +25%")
    void punishHeavyIntent() {
        SkillSynergies synergies = SkillSynergies.of(List.of(stun("기절 타격"), plain("강타", Target.EnemySingle, 1.6)));

        assertThat(synergies.targetDamageMultiplier(monster("HEAVY"))).isCloseTo(1.25, within(1e-9));
        assertThat(synergies.targetDamageMultiplier(monster("ATTACK"))).isEqualTo(1.0);
        assertThat(synergies.targetDamageMultiplier(monster("GUARD"))).isEqualTo(1.0);
    }

    @Test
    @DisplayName("재앙의 조율: 서로 다른 상태이상 3종 -> 상태이상이 걸린 적에게만 +15%")
    void afflictedTargets() {
        SkillSynergies synergies = SkillSynergies.of(List.of(bleed("출혈"), poison("독"), stun("기절")));

        SessionMonster clean = monster("ATTACK");
        SessionMonster poisoned = monster("ATTACK");
        poisoned.getBuffs().put("POISON", 3);

        assertThat(synergies.targetDamageMultiplier(clean)).isEqualTo(1.0);
        assertThat(synergies.targetDamageMultiplier(poisoned)).isCloseTo(1.15, within(1e-9));
    }

    @Test
    @DisplayName("같은 상태이상만 여럿이면 재앙의 조율은 발동하지 않는다")
    void sameStatusDoesNotCountAsVariety() {
        SkillSynergies synergies = SkillSynergies.of(List.of(bleed("a"), bleed("b"), bleed("c")));

        assertThat(synergies.getAfflictedMultiplier()).isEqualTo(1.0);
    }
}
