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

                target.damage(damage);
                log.add("%s에게 %d의 피해%s".formatted(target.getName(), damage, crit ? " (치명타!)" : ""));

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
    public int monsterAttackDamage(List<SessionMonster> monsters, int defense, int flatReduction, List<String> log) {
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

            int damage = Math.max(1, monster.getAtk() - defense - flatReduction);
            totalDamage += damage;
            log.add("%s의 공격! %d의 피해".formatted(monster.getName(), damage));
        }
        return totalDamage;
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
