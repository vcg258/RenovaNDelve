package com.renovandelve.service;

import com.renovandelve.domain.GameSkillSlotEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.engine.SessionMonster;
import com.renovandelve.engine.SkillSynergies;

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
     * 플레이어 스킬 공격 적용 (스킬 조합 시너지 포함).
     * @param synergies 보유 스킬 조합 효과: 대상 상태에 따른 피해 배율 (강공 준비 중 / 상태이상)
     */
    void applyPlayerAttack(SkillEntity skill, int attack, double critChance, double damageMultiplier,
                           double bleedMultiplier, int critBleedStacks, SkillSynergies synergies,
                           List<SessionMonster> targets, List<String> log);

    /**
     * 스킬의 상태이상 부여
     */
    void applyStatusEffect(SkillEntity skill, List<SessionMonster> targets, List<String> log);

    /**
     * 스킬의 상태이상 부여 (스킬 조합 시너지로 얹는 추가 스택 포함)
     */
    void applyStatusEffect(SkillEntity skill, int extraStacks, List<SessionMonster> targets, List<String> log);

    /**
     * 턴 종료 도트 피해 (화상 / 독)
     */
    void applyDotDamage(List<SessionMonster> monsters, List<String> log);

    /**
     * 몬스터 일제 행동 -> 플레이어가 받는 총 피해량.
     * 각 몬스터는 예고된 행동(intent)대로 움직인다: 공격 / 강공(피해 1.6배) / 방어 태세(공격하지 않음)
     * @param defense 플레이어 총 방어력
     * @param flatReduction 유물로 인한 고정 피해 감소
     * @param incomingMultiplier 플레이어가 받는 피해 배율 (방어 태세 중이면 1.0 미만)
     */
    int monsterAttackDamage(List<SessionMonster> monsters, int defense, int flatReduction,
                            double incomingMultiplier, List<String> log);

    /**
     * 다음 턴 행동 예고 결정. 전투 시작 시와 몬스터 턴이 끝날 때마다 호출한다.
     */
    void rollIntents(List<SessionMonster> monsters);

    /**
     * 보스 격노: HP가 절반 이하로 떨어지면 1회에 한해 공격력이 상승한다.
     */
    void applyEnrage(List<SessionMonster> monsters, List<String> log);

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
