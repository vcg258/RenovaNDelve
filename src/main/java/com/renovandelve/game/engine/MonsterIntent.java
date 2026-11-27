package com.renovandelve.game.engine;

/**
 * 몬스터의 이번 턴 행동 예고({@code SessionMonster.intent}).
 * 세션(Redis)과 응답에는 문자열로 저장되므로 문자열 상수로 둔다. 전투 규칙({@code BattleServiceImpl})과
 * 스킬 조합({@link SkillSynergies}), AI 도구가 모두 이 값을 쓴다.
 */
public final class MonsterIntent {
    /** 일반 공격 */
    public static final String ATTACK = "ATTACK";
    /** 강공: 피해 1.6배. 미리 보이므로 방어나 기절로 대응할 수 있다 */
    public static final String HEAVY = "HEAVY";
    /** 방어 태세: 이번 턴 받는 타격 피해 절반, 공격하지 않음 */
    public static final String GUARD = "GUARD";

    /** 강공의 피해 배율 */
    public static final double HEAVY_MULTIPLIER = 1.6;

    private MonsterIntent() {
    }

    /**
     * 예고한 행동대로 움직일 때의 공격력. 화면의 "공격 예고 N" 숫자다 (방어력·피해 하한 적용 전).
     *
     * @param intent 예고 (null이면 공격)
     * @param atk    몬스터 공격력
     * @return 강공이면 배율 적용값, 방어 태세면 0
     */
    public static int expectedAttack(String intent, int atk) {
        if (HEAVY.equals(intent)) return (int) Math.round(atk * HEAVY_MULTIPLIER);
        if (GUARD.equals(intent)) return 0;
        return atk;
    }
}
