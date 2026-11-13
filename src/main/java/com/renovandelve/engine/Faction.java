package com.renovandelve.engine;

/**
 * 세계를 이루는 세 세력. 조우 이벤트의 선택지는 각각 한 세력의 방식에 해당한다.
 *
 * - HEAVEN(천상): 질서와 심판. 안전하지만 대가로 무언가를 내려놓게 한다
 * - ABYSS(마계): 욕망과 계약. 큰 것을 주고 반드시 대가를 받아 간다
 * - HUMAN(인간): 의지와 실리. 누구의 손도 빌리지 않고 스스로 해결한다
 */
public enum Faction {
    HEAVEN("천상"),
    ABYSS("마계"),
    HUMAN("인간");

    private final String label;

    Faction(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static Faction parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
