package com.renovandelve.game.engine;

/**
 * 스킬이 거는 상태이상. 스킬 테이블의 {@code status_effect} 번호와 세션 몬스터의 버프 키를 한곳에서 정한다.
 * 버프 키는 enum 이름과 같다({@code "BLEED"} 등). 상태이상이 없는 스킬은 번호 0이고 enum 값이 없다.
 * 번호로 분기할 때는 {@link #ofCode(short)}로 enum을 얻어 enum으로 switch한다. 피해 수치와 설명은 규칙이 있는 {@code BattleServiceImpl}이 갖는다.
 */
public enum StatusEffect {
    BLEED(1, "출혈"),
    BURN(2, "화상"),
    POISON(3, "독"),
    STUN(4, "기절");

    private final short code;
    private final String label;

    StatusEffect(int code, String label) {
        this.code = (short) code;
        this.label = label;
    }

    /** @return 스킬 테이블의 상태이상 번호 */
    public short code() {
        return code;
    }

    /** @return 한국어 이름 */
    public String label() {
        return label;
    }

    /** @return 세션 몬스터의 버프 키 */
    public String buffKey() {
        return name();
    }

    /**
     * @param code 스킬 테이블의 상태이상 번호
     * @return 상태이상, 0이거나 모르는 번호면 null
     */
    public static StatusEffect ofCode(short code) {
        for (StatusEffect effect : values()) {
            if (effect.code == code) return effect;
        }
        return null;
    }

    /**
     * @param buffKey 버프 키
     * @return 한국어 이름, 상태이상이 아니면 키 그대로
     */
    public static String labelOf(String buffKey) {
        for (StatusEffect effect : values()) {
            if (effect.name().equals(buffKey)) return effect.label;
        }
        return buffKey;
    }
}
