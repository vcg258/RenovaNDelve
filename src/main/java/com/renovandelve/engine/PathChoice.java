package com.renovandelve.engine;

/**
 * 갈림길 선택. 다음 노드로 전진할 때 플레이어가 위험과 보상의 균형을 직접 고른다.
 *
 * AI는 여전히 "무엇이 나오는지(서사, 몬스터 종류)"만 정하고,
 * 길 선택은 서버가 부여하는 수치(적 강도, 보상 배율)에만 영향을 준다.
 * -> 서사/수치 분리 원칙과 비동기 프리페치를 그대로 유지하면서 선택지를 추가할 수 있다.
 */
public enum PathChoice {
    /** 정면 돌파: 기본값 */
    SAFE(1.0, 1.0, 0, false),
    /** 위험한 길: 적 HP·공격력 +25%, 점수·골드 +50%, 유물 보상 후보 +1 */
    DANGER(1.25, 1.5, 1, false),
    /** 은밀한 길: 기습으로 적 전원이 첫 턴 행동 불가, 대신 점수·골드 -30% */
    AMBUSH(1.0, 0.7, 0, true);

    private final double enemyMultiplier;
    private final double rewardMultiplier;
    private final int bonusRelicOptions;
    private final boolean ambush;

    PathChoice(double enemyMultiplier, double rewardMultiplier, int bonusRelicOptions, boolean ambush) {
        this.enemyMultiplier = enemyMultiplier;
        this.rewardMultiplier = rewardMultiplier;
        this.bonusRelicOptions = bonusRelicOptions;
        this.ambush = ambush;
    }

    public double enemyMultiplier() {
        return enemyMultiplier;
    }

    public double rewardMultiplier() {
        return rewardMultiplier;
    }

    public int bonusRelicOptions() {
        return bonusRelicOptions;
    }

    public boolean ambush() {
        return ambush;
    }

    /** 알 수 없는 값이나 null은 기본 길로 처리한다 (클라이언트 입력을 신뢰하지 않는다) */
    public static PathChoice parse(String value) {
        if (value == null || value.isBlank()) return SAFE;
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return SAFE;
        }
    }
}
