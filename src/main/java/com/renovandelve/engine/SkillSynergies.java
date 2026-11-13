package com.renovandelve.engine;

import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.skillentity.Target;
import lombok.Getter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 보유 스킬 조합 -> 전투 패시브 효과 해석기. (유물 쪽의 {@link RelicEffects}와 짝을 이룬다)
 *
 * 스킬 이름이 아니라 스킬의 성질(상태이상 종류, 대상, 계수)로 판정한다.
 * 그래서 스킬이 추가되거나 이름이 바뀌어도 조합 규칙은 그대로 동작한다.
 * 슬롯이 8개로 한정돼 있으므로, 어떤 스킬을 버리고 무엇을 남길지가 조합 선택이 된다.
 */
@Getter
public class SkillSynergies {
    // 상태이상 코드 (skill.statusEffect) - BattleServiceImpl과 같은 값
    public static final short BLEED = 1;
    public static final short BURN = 2;
    public static final short POISON = 3;
    public static final short STUN = 4;

    private static final BigDecimal HEAVY_HIT_COEFFICIENT = BigDecimal.valueOf(1.5);

    /** 조합이 하나도 없는 상태 */
    public static final SkillSynergies NONE = new SkillSynergies();

    private double bleedMultiplierBonus = 0.0;   // 피의 연계: 출혈 피해 배율에 더해진다
    private int extraBurnStacks = 0;             // 번지는 불길
    private int extraPoisonStacks = 0;           // 상처에 스민 독
    private double areaDamageMultiplier = 1.0;   // 휩쓸기: 전체 공격 스킬의 피해
    private double heavyIntentMultiplier = 1.0;  // 빈틈 찌르기: 강공을 준비 중인 적에게 주는 피해
    private double afflictedMultiplier = 1.0;    // 재앙의 조율: 상태이상이 걸린 적에게 주는 피해
    private final List<String> activeSynergies = new ArrayList<>();

    public static SkillSynergies of(Collection<SkillEntity> skills) {
        SkillSynergies synergies = new SkillSynergies();
        if (skills == null) return synergies;

        int bleed = 0, burn = 0, poison = 0, stun = 0, area = 0, heavy = 0;
        Set<Short> statusKinds = new HashSet<>();
        for (SkillEntity skill : skills) {
            if (skill == null) continue;
            switch (skill.getStatusEffect()) {
                case BLEED -> bleed++;
                case BURN -> burn++;
                case POISON -> poison++;
                case STUN -> stun++;
                default -> { /* 상태이상 없음 */ }
            }
            if (skill.getStatusEffect() > 0) statusKinds.add(skill.getStatusEffect());
            if (skill.getTarget() == Target.EnemyAll) area++;
            if (skill.getCoefficient() != null && skill.getCoefficient().compareTo(HEAVY_HIT_COEFFICIENT) >= 0) heavy++;
        }

        if (bleed >= 2) {
            synergies.bleedMultiplierBonus += 0.5;
            synergies.activeSynergies.add("피의 연계 — 출혈 피해 +50% (출혈 스킬 2개 이상)");
        }
        if (burn >= 2) {
            synergies.extraBurnStacks += 1;
            synergies.activeSynergies.add("번지는 불길 — 화상을 걸 때 1스택 추가 (화상 스킬 2개 이상)");
        }
        if (poison >= 1 && bleed >= 1) {
            synergies.extraPoisonStacks += 1;
            synergies.activeSynergies.add("상처에 스민 독 — 독을 걸 때 1스택 추가 (독 스킬 + 출혈 스킬)");
        }
        if (area >= 2) {
            synergies.areaDamageMultiplier += 0.10;
            synergies.activeSynergies.add("휩쓸기 — 전체 공격 스킬 피해 +10% (전체 공격 스킬 2개 이상)");
        }
        if (stun >= 1 && heavy >= 1) {
            synergies.heavyIntentMultiplier += 0.25;
            synergies.activeSynergies.add("빈틈 찌르기 — 강공을 준비 중인 적에게 주는 피해 +25% (기절 스킬 + 계수 150% 이상 스킬)");
        }
        if (statusKinds.size() >= 3) {
            synergies.afflictedMultiplier += 0.15;
            synergies.activeSynergies.add("재앙의 조율 — 상태이상이 걸린 적에게 주는 피해 +15% (서로 다른 상태이상 스킬 3종 이상)");
        }
        return synergies;
    }

    /** 이 스킬로 공격할 때의 피해 배율 (대상과 무관한 부분) */
    public double skillDamageMultiplier(SkillEntity skill) {
        return skill != null && skill.getTarget() == Target.EnemyAll ? areaDamageMultiplier : 1.0;
    }

    /** 대상의 상태에 따라 달라지는 피해 배율 */
    public double targetDamageMultiplier(SessionMonster target) {
        double multiplier = 1.0;
        if (heavyIntentMultiplier != 1.0 && "HEAVY".equals(target.getIntent())) {
            multiplier *= heavyIntentMultiplier;
        }
        if (afflictedMultiplier != 1.0 && isAfflicted(target.getBuffs())) {
            multiplier *= afflictedMultiplier;
        }
        return multiplier;
    }

    /** 상태이상을 걸 때 추가로 얹는 스택 */
    public int extraStacks(short statusEffect) {
        return switch (statusEffect) {
            case BURN -> extraBurnStacks;
            case POISON -> extraPoisonStacks;
            default -> 0;
        };
    }

    private static boolean isAfflicted(Map<String, Integer> buffs) {
        return buffs != null && buffs.values().stream().anyMatch(stack -> stack != null && stack > 0);
    }
}
