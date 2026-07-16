package com.renovandelve.service;

import com.renovandelve.domain.GameSkillSlotEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.engine.SessionMonster;

import java.util.List;

/**
 * 전투 수치 계산 전담 (상태 없는 순수 로직).
 * 몬스터 상태는 Redis의 GameSession이 단일 소유하며, 이 서비스는 그 위에서 계산만 수행한다.
 */
public interface BattleService {

    /**
     * 플레이어 스킬 공격 적용
     * @param skill 사용 스킬
     * @param attack 플레이어 총 공격력 (임시 버프 / 유물 반영 전 기본값)
     * @param critChance 치명타 확률 (0.0 ~ 1.0)
     * @param damageMultiplier 유물 등으로 인한 스킬 피해 배율
     * @param bleedMultiplier 출혈 피해 배율
     * @param critBleedStacks 유물 시너지(피의 사냥): 치명타 시 부여할 출혈 스택 (0이면 비활성)
     * @param targets 대상 몬스터
     * @param log 전투 로그 수집기
     */
    void applyPlayerAttack(SkillEntity skill, int attack, double critChance, double damageMultiplier,
                           double bleedMultiplier, int critBleedStacks,
                           List<SessionMonster> targets, List<String> log);

    /**
     * 스킬의 상태이상 부여
     */
    void applyStatusEffect(SkillEntity skill, List<SessionMonster> targets, List<String> log);

    /**
     * 턴 종료 도트 피해 (화상 / 독)
     */
    void applyDotDamage(List<SessionMonster> monsters, List<String> log);

    /**
     * 몬스터 일제 공격 -> 플레이어가 받는 총 피해량
     * @param defense 플레이어 총 방어력
     * @param flatReduction 유물로 인한 고정 피해 감소
     */
    int monsterAttackDamage(List<SessionMonster> monsters, int defense, int flatReduction, List<String> log);

    /**
     * 몬스터 전멸 판정
     */
    boolean isAllMonsterDead(List<SessionMonster> monsters);

    /**
     * 쿨타임 갱신 (사용 스킬은 쿨타임 부여, 나머지는 1 감소)
     * @param cooldownReduction 유물로 인한 쿨타임 감소량
     */
    void updateCoolTimes(List<GameSkillSlotEntity> slots, Long usedSlotId, int cooldownReduction);
}
