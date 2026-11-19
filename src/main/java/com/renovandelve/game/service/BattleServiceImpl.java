package com.renovandelve.game.service;

import com.renovandelve.game.dto.response.BattleKeywordDTO;
import com.renovandelve.game.engine.MonsterIntent;
import com.renovandelve.game.engine.StatusEffect;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.engine.SkillSynergies;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Random;

@Service
@Slf4j
@RequiredArgsConstructor
public class BattleServiceImpl implements BattleService {

    // 상태이상 버프 키 (SessionMonster.buffs). 번호와 이름은 StatusEffect 한곳에서 정한다
    public static final String BUFF_BLEED = StatusEffect.BLEED.buffKey(); // 출혈: 피격마다 추가 피해
    public static final String BUFF_BURN = StatusEffect.BURN.buffKey();   // 화상: 턴 종료 시 최대HP 1% (30% 확률 스택 해제)
    public static final String BUFF_POISON = StatusEffect.POISON.buffKey(); // 독: 턴 종료 시 스택만큼 피해 (영구)
    public static final String BUFF_STUN = StatusEffect.STUN.buffKey();   // 기절: 스택 턴 동안 행동 불가
    /** 보스 격노 (버프가 아니라 SessionMonster.enraged). 화면 설명용 키 */
    public static final String KEYWORD_ENRAGED = "ENRAGED";

    // 몬스터 행동 예고 (SessionMonster.intent). 값은 MonsterIntent 한곳에서 정한다
    public static final String INTENT_ATTACK = MonsterIntent.ATTACK;
    public static final String INTENT_HEAVY = MonsterIntent.HEAVY;
    public static final String INTENT_GUARD = MonsterIntent.GUARD;

    private static final double HEAVY_MULTIPLIER = 1.6;
    private static final double GUARD_DAMAGE_TAKEN = 0.5;
    private static final double ENRAGE_ATK_MULTIPLIER = 1.3;
    // 방어력이 공격력을 넘어서면 모든 피해가 1이 되어 후반 전투가 무의미해진다.
    // 공격력의 일정 비율은 방어력과 무관하게 항상 들어오도록 하한을 둔다.
    private static final double MIN_DAMAGE_RATIO = 0.15;

    private static final double BURN_AUTO_CLEAR_CHANCE = 0.30;
    private static final double CRIT_MULTIPLIER = 1.5;

    private final Random random = new Random();

    /**
     * 플레이어 공격 (스킬 조합 시너지 없음). 시너지를 쓰는 오버로드에 위임한다.
     */
    @Override
    public void applyPlayerAttack(SkillEntity skill, int attack, double critChance, double damageMultiplier,
                                  double bleedMultiplier, int critBleedStacks,
                                  List<SessionMonster> targets, List<String> log) {
        applyPlayerAttack(skill, attack, critChance, damageMultiplier, bleedMultiplier, critBleedStacks,
                SkillSynergies.NONE, targets, log);
    }

    /**
     * 스킬로 대상들을 공격한다. 계수 × 공격력 × 배율에서 방어력을 빼고(최소 1), 치명타·다단 히트·
     * 방어 태세(피해 절반)·스킬 조합 시너지를 반영한 뒤 상태이상을 건다.
     *
     * @param skill            쓴 스킬
     * @param attack           플레이어 공격력
     * @param critChance       치명타 확률
     * @param damageMultiplier 유물·가호 피해 배율
     * @param bleedMultiplier  출혈 피해 배율
     * @param critBleedStacks  치명타 시 추가 출혈 스택 (피의 사냥 시너지)
     * @param synergies        스킬 조합 시너지
     * @param targets          대상
     * @param log              전투 기록
     */
    @Override
    public void applyPlayerAttack(SkillEntity skill, int attack, double critChance, double damageMultiplier,
                                  double bleedMultiplier, int critBleedStacks, SkillSynergies synergies,
                                  List<SessionMonster> targets, List<String> log) {
        SkillSynergies combo = synergies == null ? SkillSynergies.NONE : synergies;
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
                // [스킬 조합] 강공을 준비 중이거나 상태이상이 걸린 적에게는 더 깊이 들어간다
                double comboMultiplier = combo.targetDamageMultiplier(target);
                if (comboMultiplier != 1.0) raw = (int) (raw * comboMultiplier);
                int damage = Math.max(1, raw - target.getDef());

                // 방어 태세인 적은 타격 피해를 절반만 받는다 (출혈·화상·독은 그대로 들어간다)
                boolean guarding = INTENT_GUARD.equals(target.getIntent());
                if (guarding) {
                    damage = Math.max(1, (int) (damage * GUARD_DAMAGE_TAKEN));
                }

                target.damage(damage);
                log.add("%s에게 %d의 피해%s%s%s".formatted(target.getName(), damage,
                        crit ? " (치명타!)" : "", guarding ? " (방어 태세에 막힘)" : "",
                        comboMultiplier != 1.0 ? " (스킬 조합 +%d%%)".formatted(Math.round((comboMultiplier - 1) * 100)) : ""));

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

    /**
     * 스킬의 상태이상을 건다 (추가 스택 없음).
     */
    @Override
    public void applyStatusEffect(SkillEntity skill, List<SessionMonster> targets, List<String> log) {
        applyStatusEffect(skill, 0, targets, log);
    }

    /**
     * 스킬의 상태이상을 살아 있는 대상에게 건다.
     *
     * @param skill       쓴 스킬
     * @param extraStacks 시너지로 늘어나는 스택
     * @param targets     대상
     * @param log         전투 기록
     */
    @Override
    public void applyStatusEffect(SkillEntity skill, int extraStacks, List<SessionMonster> targets, List<String> log) {
        if (StatusEffect.ofCode(skill.getStatusEffect()) == null) return;

        String buffName = resolveBuffName(skill.getStatusEffect());
        int stack = Math.max(1, skill.getEffectStack()) + Math.max(0, extraStacks);

        for (SessionMonster target : targets) {
            if (!target.isAlive()) continue;
            Map<String, Integer> buffs = target.getBuffs();
            buffs.merge(buffName, stack, Integer::sum);
            log.add("%s에게 %s %d스택 부여".formatted(target.getName(), koreanBuffName(buffName), stack));
        }
    }

    /**
     * 턴이 끝날 때 지속 피해를 준다: 화상(최대 HP 1% × 스택, 방어 무시), 독(고정), 출혈(스택만큼, 1씩 감소).
     *
     * @param monsters 몬스터
     * @param log      전투 기록
     */
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

    /**
     * 몬스터들이 예고한 행동대로 플레이어를 공격한 총 피해를 계산한다.
     * 기절한 적은 쉬고, 방어 태세인 적은 공격하지 않으며, 강공은 1.6배다. 한 마리 피해는 적 공격력의 15% 밑으로 내려가지 않는다.
     *
     * @param monsters           몬스터
     * @param defense            플레이어 방어력
     * @param flatReduction      유물로 줄어드는 고정 피해
     * @param incomingMultiplier 받는 피해 배율 (방어 태세 0.4, 가호 등)
     * @param log                전투 기록
     * @return 총 피해
     */
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
            log.add("%s의 %s! %d의 피해".formatted(monster.getName(), attackName(monster, heavy), damage));
        }
        return totalDamage;
    }

    /**
     * 공격 기록에 쓸 이름. 기술 이름이 있는 적(망령)은 그 기술을 외치고, 강공이면 뒤에 "강공"이 붙는다.
     *
     * @param monster 공격하는 적
     * @param heavy   강공이면 true
     * @return 기록에 쓸 공격 이름
     */
    private String attackName(SessionMonster monster, boolean heavy) {
        List<String> moves = monster.getMoves();
        if (moves == null || moves.isEmpty()) return heavy ? "강공" : "공격";
        String move = "[" + moves.get(random.nextInt(moves.size())) + "]";
        return heavy ? move + " 강공" : move;
    }

    /**
     * 살아 있는 몬스터의 다음 턴 행동을 정한다 (공격 / 강공 / 방어).
     *
     * @param monsters 몬스터
     */
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

    /**
     * 보스가 HP 절반 이하가 되면 한 번 격노해 공격력이 30% 오른다.
     *
     * @param monsters 몬스터
     * @param log      전투 기록
     */
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

    /**
     * 살아 있는 몬스터가 없는지 확인한다.
     *
     * @param monsters 몬스터
     * @return 모두 쓰러졌으면 true
     */
    @Override
    public boolean isAllMonsterDead(List<SessionMonster> monsters) {
        return monsters.stream().noneMatch(SessionMonster::isAlive);
    }

    /**
     * 모든 스킬의 쿨타임을 주어진 턴 수만큼 줄인다 (숨 고르기 등).
     *
     * @param slots 스킬 슬롯
     * @param turns 줄일 턴 수
     */
    @Override
    public void tickCooldowns(List<GameSkillSlotEntity> slots, int turns) {
        for (int i = 0; i < turns; i++) {
            for (GameSkillSlotEntity slot : slots) {
                slot.tickCooldown();
            }
        }
    }

    /**
     * 전투 키워드(상태이상, 격노) 설명. 수치는 이 클래스의 규칙 상수에서 만들어 화면 설명과 실제 규칙이 어긋나지 않게 한다.
     *
     * @return 키워드 목록
     */
    @Override
    public List<BattleKeywordDTO> keywords() {
        return List.of(
                new BattleKeywordDTO(BUFF_BLEED, StatusEffect.BLEED.label(),
                        "공격을 받을 때마다 스택만큼 추가 피해를 입는다."),
                new BattleKeywordDTO(BUFF_BURN, StatusEffect.BURN.label(),
                        "턴이 끝날 때 최대 HP의 1%% × 스택만큼 피해(방어 무시). %d%% 확률로 1스택씩 꺼진다."
                                .formatted(Math.round(BURN_AUTO_CLEAR_CHANCE * 100))),
                new BattleKeywordDTO(BUFF_POISON, StatusEffect.POISON.label(),
                        "턴이 끝날 때 스택만큼 피해(방어 무시). 사라지지 않는다."),
                new BattleKeywordDTO(BUFF_STUN, StatusEffect.STUN.label(),
                        "이번 턴 행동하지 못한다. 한 턴 쉴 때마다 1스택씩 줄어든다."),
                new BattleKeywordDTO(KEYWORD_ENRAGED, "격노",
                        "보스의 HP가 절반 아래로 떨어져 공격력이 %d%% 올랐다."
                                .formatted(Math.round((ENRAGE_ATK_MULTIPLIER - 1) * 100))));
    }

    @Override
    public void updateCoolTimes(List<GameSkillSlotEntity> slots, Long usedSlotId, int cooldownReduction) {
        for (GameSkillSlotEntity slot : slots) {
            if (slot.getSlotId().equals(usedSlotId)) {
                slot.startCooldown(cooldownReduction);
            } else {
                slot.tickCooldown();
            }
        }
    }

    /* HELPER METHODS */

    /**
     * 스킬의 상태이상 번호를 버프 키로 바꾼다 (1 출혈, 2 화상, 3 독, 4 기절).
     *
     * @param statusEffect 상태이상 번호
     * @return 버프 키
     */
    private String resolveBuffName(short statusEffect) {
        StatusEffect effect = StatusEffect.ofCode(statusEffect);
        if (effect == null) throw new IllegalArgumentException("알 수 없는 상태이상 코드: " + statusEffect);
        return effect.buffKey();
    }

    /**
     * 버프 키를 전투 기록용 한국어로 바꾼다.
     *
     * @param buffName 버프 키
     * @return 한국어 이름
     */
    private String koreanBuffName(String buff) {
        return StatusEffect.labelOf(buff);
    }
}
