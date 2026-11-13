package com.renovandelve.service;

import com.renovandelve.domain.GameSkillSlotEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.engine.SessionMonster;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@Log4j2
@RequiredArgsConstructor
public class BattleServiceImpl implements BattleService {

    // 상태이상 코드 (skill.statusEffect)
    private static final short BLEED = 1;  // 출혈: 피격마다 추가 피해
    private static final short BURN = 2;   // 화상: 턴 종료 시 최대HP 1% (30% 확률 스택 해제)
    private static final short POISON = 3; // 독: 턴 종료 시 스택만큼 피해 (영구)
    private static final short STUN = 4;   // 기절: 스택 턴 동안 행동 불가

    public static final String BUFF_BLEED = "BLEED";
    public static final String BUFF_BURN = "BURN";
    public static final String BUFF_POISON = "POISON";
    public static final String BUFF_STUN = "STUN";

    // 몬스터 행동 예고 (SessionMonster.intent)
    public static final String INTENT_ATTACK = "ATTACK"; // 일반 공격
    public static final String INTENT_HEAVY = "HEAVY";   // 강공: 피해 1.6배. 미리 보이므로 방어/기절로 대응할 수 있다
    public static final String INTENT_GUARD = "GUARD";   // 방어 태세: 이번 턴 받는 타격 피해 절반, 공격하지 않음

    private static final double HEAVY_MULTIPLIER = 1.6;
    private static final double GUARD_DAMAGE_TAKEN = 0.5;
    private static final double ENRAGE_ATK_MULTIPLIER = 1.3;
    // 방어력이 공격력을 넘어서면 모든 피해가 1이 되어 후반 전투가 무의미해진다.
    // 공격력의 일정 비율은 방어력과 무관하게 항상 들어오도록 하한을 둔다.
    private static final double MIN_DAMAGE_RATIO = 0.15;

    private static final double BURN_AUTO_CLEAR_CHANCE = 0.30;
    private static final double CRIT_MULTIPLIER = 1.5;

    private final Random random = new Random();

    @Override
    public void applyPlayerAttack(SkillEntity skill, int attack, double critChance, double damageMultiplier,
                                  double bleedMultiplier, int critBleedStacks,
                                  List<SessionMonster> targets, List<String> log) {
        BigDecimal coefficient = skill.getCoefficient() == null ? BigDecimal.ONE : skill.getCoefficient();
        int baseDamage = coefficient
                .multiply(BigDecimal.valueOf(attack))
                .multiply(BigDecimal.valueOf(damageMultiplier))
                .setScale(0, RoundingMode.FLOOR)
                .intValue();

        int attackCount = Math.max(1, skill.getAttackCount());
        for (int hit = 0; hit < attackCount; hit++) {
            for (SessionMonster target : targets) {
                if (!target.isAlive()) continue;

                boolean crit = random.nextDouble() < critChance;
                int raw = crit ? (int) (baseDamage * CRIT_MULTIPLIER) : baseDamage;
                int damage = Math.max(1, raw - target.getDef());

                // 방어 태세인 적은 타격 피해를 절반만 받는다 (출혈·화상·독은 그대로 들어간다)
                boolean guarding = INTENT_GUARD.equals(target.getIntent());
                if (guarding) {
                    damage = Math.max(1, (int) (damage * GUARD_DAMAGE_TAKEN));
                }

                target.damage(damage);
                log.add("%s에게 %d의 피해%s%s".formatted(target.getName(), damage,
                        crit ? " (치명타!)" : "", guarding ? " (방어 태세에 막힘)" : ""));

                // [유물 시너지] 피의 사냥: 치명타가 상처를 찢어 출혈을 남긴다
                if (crit && critBleedStacks > 0 && target.isAlive()) {
                    target.getBuffs().merge(BUFF_BLEED, critBleedStacks, Integer::sum);
                    log.add("피의 사냥 발동! %s에게 출혈 %d스택".formatted(target.getName(), critBleedStacks));
                }

                // 출혈 스택이 있으면 피격마다 추가 피해
                Integer bleed = target.getBuffs().get(BUFF_BLEED);
                if (target.isAlive() && bleed != null && bleed > 0) {
                    int bleedDamage = Math.max(1, (int) (bleed * bleedMultiplier));
                    target.damage(bleedDamage);
                    log.add("%s 출혈 피해 %d".formatted(target.getName(), bleedDamage));
                }

                if (!target.isAlive()) {
                    log.add("%s 처치!".formatted(target.getName()));
                }
            }
        }
    }

    @Override
    public void applyStatusEffect(SkillEntity skill, List<SessionMonster> targets, List<String> log) {
        if (skill.getStatusEffect() == 0) return;

        String buffName = resolveBuffName(skill.getStatusEffect());
        int stack = Math.max(1, skill.getEffectStack());

        for (SessionMonster target : targets) {
            if (!target.isAlive()) continue;
            Map<String, Integer> buffs = target.getBuffs();
            buffs.merge(buffName, stack, Integer::sum);
            log.add("%s에게 %s %d스택 부여".formatted(target.getName(), koreanBuffName(buffName), stack));
        }
    }

    @Override
    public void applyDotDamage(List<SessionMonster> monsters, List<String> log) {
        for (SessionMonster monster : monsters) {
            if (!monster.isAlive()) continue;
            Map<String, Integer> buffs = monster.getBuffs();

            // 화상: 최대 HP 1% x 스택 (방어 무시)
            Integer burn = buffs.get(BUFF_BURN);
            if (burn != null && burn > 0) {
                int burnDamage = Math.max(1, (int) (monster.getMaxHp() * 0.01) * burn);
                monster.damage(burnDamage);
                log.add("%s 화상 피해 %d".formatted(monster.getName(), burnDamage));
                if (random.nextDouble() < BURN_AUTO_CLEAR_CHANCE) {
                    if (burn - 1 <= 0) buffs.remove(BUFF_BURN);
                    else buffs.put(BUFF_BURN, burn - 1);
                }
            }

            // 독: 스택만큼 피해 (영구 지속, 방어 무시)
            Integer poison = buffs.get(BUFF_POISON);
            if (monster.isAlive() && poison != null && poison > 0) {
                monster.damage(poison);
                log.add("%s 중독 피해 %d".formatted(monster.getName(), poison));
            }

            if (!monster.isAlive()) {
                log.add("%s 쓰러짐!".formatted(monster.getName()));
            }
        }
    }

    @Override
    public int monsterAttackDamage(List<SessionMonster> monsters, int defense, int flatReduction,
                                   double incomingMultiplier, List<String> log) {
        int totalDamage = 0;
        for (SessionMonster monster : monsters) {
            if (!monster.isAlive()) continue;

            // 기절 상태면 이번 턴 행동 불가 (스택 1 감소)
            Integer stun = monster.getBuffs().get(BUFF_STUN);
            if (stun != null && stun > 0) {
                if (stun - 1 <= 0) monster.getBuffs().remove(BUFF_STUN);
                else monster.getBuffs().put(BUFF_STUN, stun - 1);
                log.add("%s은(는) 기절해서 움직이지 못한다".formatted(monster.getName()));
                continue;
            }

            if (INTENT_GUARD.equals(monster.getIntent())) {
                log.add("%s은(는) 방어 태세를 유지한다".formatted(monster.getName()));
                continue;
            }

            boolean heavy = INTENT_HEAVY.equals(monster.getIntent());
            int power = heavy ? (int) Math.round(monster.getAtk() * HEAVY_MULTIPLIER) : monster.getAtk();
            int floor = Math.max(1, (int) Math.ceil(power * MIN_DAMAGE_RATIO));
            int damage = Math.max(floor, power - defense - flatReduction);
            damage = Math.max(1, (int) Math.round(damage * incomingMultiplier));

            totalDamage += damage;
            log.add("%s의 %s! %d의 피해".formatted(monster.getName(), heavy ? "강공" : "공격", damage));
        }
        return totalDamage;
    }

    @Override
    public void rollIntents(List<SessionMonster> monsters) {
        for (SessionMonster monster : monsters) {
            if (!monster.isAlive()) continue;
            monster.setIntent(nextIntent(monster.getMonsterType(), monster.getIntent(), random.nextDouble()));
        }
    }

    /**
     * 등급별 행동 확률. 강한 적일수록 강공 빈도가 높다.
     * 같은 특수 행동(강공/방어)을 두 턴 연속으로 하지 않게 해, 예고를 보고 대응할 틈을 보장한다.
     * (난수를 인자로 받는 순수 함수 -> 단위 테스트 가능)
     */
    static String nextIntent(short monsterType, String previous, double roll) {
        double heavyChance = switch (monsterType) { case 2 -> 0.30; case 1 -> 0.22; default -> 0.12; };
        double guardChance = switch (monsterType) { case 2 -> 0.10; case 1 -> 0.15; default -> 0.12; };

        if (roll < heavyChance) {
            return INTENT_HEAVY.equals(previous) ? INTENT_ATTACK : INTENT_HEAVY;
        }
        if (roll < heavyChance + guardChance) {
            return INTENT_GUARD.equals(previous) ? INTENT_ATTACK : INTENT_GUARD;
        }
        return INTENT_ATTACK;
    }

    @Override
    public void applyEnrage(List<SessionMonster> monsters, List<String> log) {
        for (SessionMonster monster : monsters) {
            if (!monster.isAlive() || monster.getMonsterType() != 2 || monster.isEnraged()) continue;
            if (monster.getCurrentHp() * 2 <= monster.getMaxHp()) {
                monster.setEnraged(true);
                monster.setAtk((int) Math.round(monster.getAtk() * ENRAGE_ATK_MULTIPLIER));
                log.add("%s이(가) 격노한다! 공격력이 크게 상승했다".formatted(monster.getName()));
            }
        }
    }

    @Override
    public boolean isAllMonsterDead(List<SessionMonster> monsters) {
        return monsters.stream().noneMatch(SessionMonster::isAlive);
    }

    @Override
    public void updateCoolTimes(List<GameSkillSlotEntity> slots, Long usedSlotId, int cooldownReduction) {
        for (GameSkillSlotEntity slot : slots) {
            if (slot.getSlotId().equals(usedSlotId)) {
                int cooldown = slot.getSkill().getCooldown();
                if (cooldown > 0) {
                    slot.setCoolTime(Math.max(1, cooldown - cooldownReduction));
                } else {
                    slot.setCoolTime(0);
                }
            } else {
                slot.setCoolTime(Math.max(0, slot.getCoolTime() - 1));
            }
        }
    }

    /* HELPER METHODS */

    private String resolveBuffName(short statusEffect) {
        return switch (statusEffect) {
            case BLEED -> BUFF_BLEED;
            case BURN -> BUFF_BURN;
            case POISON -> BUFF_POISON;
            case STUN -> BUFF_STUN;
            default -> throw new IllegalArgumentException("알 수 없는 상태이상 코드: " + statusEffect);
        };
    }

    private String koreanBuffName(String buff) {
        return switch (buff) {
            case BUFF_BLEED -> "출혈";
            case BUFF_BURN -> "화상";
            case BUFF_POISON -> "독";
            case BUFF_STUN -> "기절";
            default -> buff;
        };
    }
}
