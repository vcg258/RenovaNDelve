package com.renovandelve.dto.response;

import java.util.Map;

/**
 * 전투 중 클라이언트에 노출되는 몬스터 상태.
 * key = 전투 세션 내 식별자 (동일 몬스터가 여러 마리 나올 수 있으므로 monsterId와 별개)
 */
public record BattleMonsterDTO(
        int key,
        Long monsterId,
        String name,
        short monsterType,      // 0 일반 / 1 엘리트 / 2 보스
        int maxHp,
        int currentHp,
        int atk,
        int def,
        Map<String, Integer> buffs, // BLEED / BURN / POISON / STUN -> 스택
        String description,
        String intent,          // 이번 턴 행동 예고: ATTACK(공격) / HEAVY(강공) / GUARD(방어 태세)
        boolean enraged         // 보스 격노 상태
) {
}
