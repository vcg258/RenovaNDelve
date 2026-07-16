package com.renovandelve.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 전투 세션 내 몬스터 상태 (Redis 저장).
 * key = 세션 내 식별자 (동일 몬스터가 여러 마리 나올 수 있으므로 monsterId와 별개)
 */
@Data
@NoArgsConstructor
public class SessionMonster {
    private int key;
    private Long monsterId;
    private String name;
    private short monsterType; // 0 일반 / 1 엘리트 / 2 보스
    private int maxHp;
    private int currentHp;
    private int atk;
    private int def;
    private int scoreReward;
    private String description;
    private Map<String, Integer> buffs = new HashMap<>(); // BLEED/BURN/POISON/STUN -> 스택

    public boolean isAlive() {
        return currentHp > 0;
    }

    public void damage(int amount) {
        this.currentHp = Math.max(0, this.currentHp - Math.max(0, amount));
    }
}
