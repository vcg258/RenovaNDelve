package com.renovandelve.service;

import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.skillentity.Target;
import com.renovandelve.engine.SessionMonster;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 전투 계산 단위 테스트.
 * BattleServiceImpl은 DB/Redis 의존이 없는 순수 계산이므로 스프링 컨텍스트 없이 검증한다.
 * (치명타 확률을 0 또는 1로 고정해 난수 영향을 제거)
 */
class BattleServiceImplTest {

    private final BattleServiceImpl battleService = new BattleServiceImpl();

    private SessionMonster monster(int hp, int atk, int def, short type) {
        SessionMonster monster = new SessionMonster();
        monster.setName("테스트 몬스터");
        monster.setMonsterType(type);
        monster.setMaxHp(hp);
        monster.setCurrentHp(hp);
        monster.setAtk(atk);
        monster.setDef(def);
        monster.setIntent(BattleServiceImpl.INTENT_ATTACK);
        return monster;
    }

    private SkillEntity skill(double coefficient, int attackCount) {
        return SkillEntity.builder()
                .name("테스트 스킬").target(Target.EnemySingle)
                .coefficient(BigDecimal.valueOf(coefficient)).attackCount(attackCount)
                .build();
    }

    @Test
    @DisplayName("플레이어 피해 = 공격력 x 계수 - 방어력")
    void playerDamageFormula() {
        SessionMonster target = monster(100, 10, 3, (short) 0);

        battleService.applyPlayerAttack(skill(1.6, 1), 20, 0.0, 1.0, 1.0, 0, List.of(target), new ArrayList<>());

        // 20 x 1.6 = 32, 방어 3 -> 29
        assertThat(target.getCurrentHp()).isEqualTo(71);
    }

    @Test
    @DisplayName("방어력이 피해보다 커도 최소 1의 피해는 들어간다")
    void playerDamageHasMinimum() {
        SessionMonster target = monster(100, 10, 999, (short) 0);

        battleService.applyPlayerAttack(skill(1.0, 3), 10, 0.0, 1.0, 1.0, 0, List.of(target), new ArrayList<>());

        assertThat(target.getCurrentHp()).isEqualTo(97); // 3타 x 1
    }

    @Test
    @DisplayName("치명타는 1.5배 피해")
    void criticalHit() {
        SessionMonster target = monster(100, 10, 0, (short) 0);

        battleService.applyPlayerAttack(skill(1.0, 1), 20, 1.0, 1.0, 1.0, 0, List.of(target), new ArrayList<>());

        assertThat(target.getCurrentHp()).isEqualTo(70);
    }

    @Test
    @DisplayName("방어 태세인 적은 타격 피해를 절반만 받지만 출혈은 그대로 받는다")
    void guardingMonsterHalvesHitDamageOnly() {
        SessionMonster target = monster(100, 10, 0, (short) 0);
        target.setIntent(BattleServiceImpl.INTENT_GUARD);
        target.getBuffs().put(BattleServiceImpl.BUFF_BLEED, 4);

        battleService.applyPlayerAttack(skill(1.0, 1), 20, 0.0, 1.0, 1.0, 0, List.of(target), new ArrayList<>());

        // 타격 20 -> 10, 출혈 4는 감소 없음
        assertThat(target.getCurrentHp()).isEqualTo(86);
    }

    @Test
    @DisplayName("강공 예고는 피해 1.6배, 방어 태세 예고는 공격하지 않는다")
    void monsterIntentsDecideDamage() {
        SessionMonster attacker = monster(50, 20, 0, (short) 0);
        SessionMonster heavy = monster(50, 20, 0, (short) 1);
        heavy.setIntent(BattleServiceImpl.INTENT_HEAVY);
        SessionMonster guard = monster(50, 20, 0, (short) 0);
        guard.setIntent(BattleServiceImpl.INTENT_GUARD);

        int damage = battleService.monsterAttackDamage(List.of(attacker, heavy, guard), 5, 0, 1.0, new ArrayList<>());

        // 일반 20-5=15, 강공 32-5=27, 방어 0
        assertThat(damage).isEqualTo(42);
    }

    @Test
    @DisplayName("플레이어 방어 태세는 받는 피해를 배율만큼 줄인다")
    void playerGuardReducesIncomingDamage() {
        SessionMonster heavy = monster(50, 50, 0, (short) 2);
        heavy.setIntent(BattleServiceImpl.INTENT_HEAVY);

        int normal = battleService.monsterAttackDamage(List.of(heavy), 0, 0, 1.0, new ArrayList<>());
        int guarded = battleService.monsterAttackDamage(List.of(heavy), 0, 0, 0.4, new ArrayList<>());

        assertThat(normal).isEqualTo(80);
        assertThat(guarded).isEqualTo(32);
    }

    @Test
    @DisplayName("방어력이 공격력보다 높아도 공격력의 15%는 최소 피해로 들어온다")
    void incomingDamageFloor() {
        SessionMonster attacker = monster(50, 40, 0, (short) 0);

        int damage = battleService.monsterAttackDamage(List.of(attacker), 999, 10, 1.0, new ArrayList<>());

        assertThat(damage).isEqualTo(6); // ceil(40 x 0.15)
    }

    @Test
    @DisplayName("기절한 몬스터는 행동하지 못하고 스택이 1 줄어든다")
    void stunnedMonsterSkipsTurn() {
        SessionMonster stunned = monster(50, 20, 0, (short) 0);
        stunned.getBuffs().put(BattleServiceImpl.BUFF_STUN, 2);

        int damage = battleService.monsterAttackDamage(List.of(stunned), 0, 0, 1.0, new ArrayList<>());

        assertThat(damage).isZero();
        assertThat(stunned.getBuffs()).containsEntry(BattleServiceImpl.BUFF_STUN, 1);
    }

    @Test
    @DisplayName("독은 스택만큼 방어 무시 피해를 주고 사라지지 않는다")
    void poisonDot() {
        SessionMonster target = monster(100, 10, 999, (short) 0);
        target.getBuffs().put(BattleServiceImpl.BUFF_POISON, 7);

        battleService.applyDotDamage(List.of(target), new ArrayList<>());

        assertThat(target.getCurrentHp()).isEqualTo(93);
        assertThat(target.getBuffs()).containsEntry(BattleServiceImpl.BUFF_POISON, 7);
    }

    @Test
    @DisplayName("보스는 HP 절반 이하에서 한 번만 격노한다")
    void bossEnragesOnce() {
        SessionMonster boss = monster(200, 30, 0, (short) 2);
        SessionMonster elite = monster(200, 30, 0, (short) 1);
        boss.setCurrentHp(100);
        elite.setCurrentHp(10);

        battleService.applyEnrage(List.of(boss, elite), new ArrayList<>());
        battleService.applyEnrage(List.of(boss, elite), new ArrayList<>());

        assertThat(boss.isEnraged()).isTrue();
        assertThat(boss.getAtk()).isEqualTo(39); // 30 x 1.3, 두 번 호출해도 중복 적용 없음
        assertThat(elite.isEnraged()).isFalse();
        assertThat(elite.getAtk()).isEqualTo(30);
    }

    @Test
    @DisplayName("같은 특수 행동을 두 턴 연속 예고하지 않는다")
    void intentDoesNotRepeatSpecialAction() {
        short boss = 2;
        // roll 0.0 -> 강공 구간
        assertThat(BattleServiceImpl.nextIntent(boss, BattleServiceImpl.INTENT_ATTACK, 0.0))
                .isEqualTo(BattleServiceImpl.INTENT_HEAVY);
        assertThat(BattleServiceImpl.nextIntent(boss, BattleServiceImpl.INTENT_HEAVY, 0.0))
                .isEqualTo(BattleServiceImpl.INTENT_ATTACK);
        // 보스: 강공 0.30, 방어 0.10 -> roll 0.35는 방어 구간
        assertThat(BattleServiceImpl.nextIntent(boss, null, 0.35))
                .isEqualTo(BattleServiceImpl.INTENT_GUARD);
        assertThat(BattleServiceImpl.nextIntent(boss, BattleServiceImpl.INTENT_GUARD, 0.35))
                .isEqualTo(BattleServiceImpl.INTENT_ATTACK);
        assertThat(BattleServiceImpl.nextIntent(boss, null, 0.99))
                .isEqualTo(BattleServiceImpl.INTENT_ATTACK);
    }
}
