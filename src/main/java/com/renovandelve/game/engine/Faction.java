package com.renovandelve.game.engine;

/**
 * 세계를 이루는 세 세력. 조우 이벤트의 선택지는 각각 한 세력의 방식에 해당한다.
 *
 * - HEAVEN(천상): 질서와 심판. 안전하지만 대가로 무언가를 내려놓게 한다
 * - ABYSS(마계): 욕망과 계약. 큰 것을 주고 반드시 대가를 받아 간다
 * - HUMAN(인간): 의지와 실리. 누구의 손도 빌리지 않고 스스로 해결한다
 */
public enum Faction {
    HEAVEN("천상", "질서와 자비. 조우에서 대체로 안전하지만 무언가를 내려놓아야 할 수 있다."),
    ABYSS("마계", "욕망과 거래. 조우에서 크게 얻거나 크게 잃는다."),
    HUMAN("인간", "의지와 실리. 누구의 힘도 빌리지 않고 스스로 챙긴다.");

    private final String label;
    private final String tendency;

    Faction(String label, String tendency) {
        this.label = label;
        this.tendency = tendency;
    }

    /** 조우 선택지에서 이 세력의 방식이 보이는 경향 (화면 안내용) */
    public String tendency() {
        return tendency;
    }

    /**
     * 화면과 AI 기록에 쓰는 한국어 이름 (천상 / 마계 / 인간).
     *
     * @return 이름
     */
    public String label() {
        return label;
    }

    /**
     * 문자열을 세력으로 바꾼다. 알 수 없는 값이나 null이면 null.
     *
     * @param value 세력 이름
     * @return 세력 또는 null
     */
    public static Faction parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
