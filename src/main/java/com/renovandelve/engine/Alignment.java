package com.renovandelve.engine;

/**
 * 플레이어의 성향. 조우 이벤트에서 어느 세력의 방식을 택했는지가 누적된다.
 *
 * 성향은 두 군데에 쓰인다.
 * 1) 서사: 누적된 선택이 AI 대화 기록에 남아 이후 장면과 결말의 어조가 달라진다 (AI의 영역)
 * 2) 수치: 한 세력에 충분히 기울면 그 세력의 가호가 붙는다 (서버의 영역, 이 클래스)
 *
 * 가호는 "가장 높은 성향이 임계치 이상이고 단독 1위"일 때만 발동한다.
 * 모든 선택지를 골고루 누르면 아무 가호도 얻지 못하므로, 한 길을 고집할 이유가 생긴다.
 */
public record Alignment(int heaven, int abyss, int human) {
    public static final int BLESSING_THRESHOLD = 3;

    private static final double HEAVEN_VICTORY_HEAL_RATIO = 0.06;
    private static final double ABYSS_ATTACK_MULTIPLIER = 1.12;
    private static final double ABYSS_DAMAGE_TAKEN_MULTIPLIER = 1.10;
    private static final double HUMAN_GOLD_MULTIPLIER = 1.20;

    /** 가호를 주는 세력. 없으면 null */
    public Faction dominant() {
        int max = Math.max(heaven, Math.max(abyss, human));
        if (max < BLESSING_THRESHOLD) return null;

        int holders = (heaven == max ? 1 : 0) + (abyss == max ? 1 : 0) + (human == max ? 1 : 0);
        if (holders > 1) return null; // 동률이면 어느 쪽에도 속하지 못한 것

        if (heaven == max) return Faction.HEAVEN;
        if (abyss == max) return Faction.ABYSS;
        return Faction.HUMAN;
    }

    /** 마계의 낙인: 공격력 상승 */
    public double attackMultiplier() {
        return dominant() == Faction.ABYSS ? ABYSS_ATTACK_MULTIPLIER : 1.0;
    }

    /** 마계의 낙인의 대가: 받는 피해 증가 */
    public double damageTakenMultiplier() {
        return dominant() == Faction.ABYSS ? ABYSS_DAMAGE_TAKEN_MULTIPLIER : 1.0;
    }

    /** 인간의 의지: 골드 획득 증가 */
    public double goldMultiplier() {
        return dominant() == Faction.HUMAN ? HUMAN_GOLD_MULTIPLIER : 1.0;
    }

    /** 천상의 가호: 전투 승리 시 최대 HP 대비 회복 비율 */
    public double victoryHealRatio() {
        return dominant() == Faction.HEAVEN ? HEAVEN_VICTORY_HEAL_RATIO : 0.0;
    }

    /** 화면과 AI 안내자에게 보여줄 가호 설명. 없으면 null */
    public String blessing() {
        Faction dominant = dominant();
        if (dominant == null) return null;
        return switch (dominant) {
            case HEAVEN -> "천상의 가호 — 전투에서 승리할 때마다 최대 HP의 6% 회복";
            case ABYSS -> "마계의 낙인 — 공격력 +12%, 대신 받는 피해 +10%";
            case HUMAN -> "인간의 의지 — 골드 획득 +20%";
        };
    }

    /** AI에게 넘기는 한 줄 요약 */
    public String describe() {
        Faction dominant = dominant();
        return "천상 %d / 마계 %d / 인간 %d%s".formatted(heaven, abyss, human,
                dominant == null ? " (아직 어느 쪽에도 기울지 않음)" : " (" + dominant.label() + " 쪽으로 기울었음)");
    }
}
