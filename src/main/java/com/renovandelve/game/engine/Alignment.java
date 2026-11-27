package com.renovandelve.game.engine;

/**
 * 플레이어의 성향. 조우 이벤트에서 어느 세력의 방식을 택했는지가 누적된다.
 *
 * 성향은 두 군데에 쓰인다.
 * 1) 서사: 누적된 선택이 AI 대화 기록에 남아 이후 장면과 결말의 어조가 달라진다 (AI의 영역)
 * 2) 수치: 한 세력에 충분히 기울면 그 세력의 가호가 붙는다 (서버의 영역, 이 클래스)
 *
 * 가호는 "가장 높은 성향이 임계치 이상이고 단독 1위"일 때만 발동한다.
 * 모든 선택지를 골고루 누르면 아무 가호도 얻지 못하므로, 한 길을 고집할 이유가 생긴다.
 *
 * 가호는 3단계다. 단독 1위인 세력의 점수가 3 / 7 / 12에 닿을 때마다 한 단계씩 오르고,
 * 3단계에서는 전투 보상 후보가 하나 늘어난다. 1단계 수치는 단계가 없던 때와 같다.
 *
 * 3단계를 넘어서도 같은 세력을 고르면 한 번마다 그 세력의 헌신 보상을 받는다(최대 HP / 공격력 / 골드).
 * 3단계에 닿은 뒤 남은 조우에서도 성향 선택이 의미를 갖게 하려는 것이다.
 * (기준이 3 / 6 / 9였을 때 시뮬레이션에서 3단계 도달이 평균 31~37번째 노드였고,
 *  그 뒤 2~4번의 조우는 어느 쪽을 골라도 차이가 없었다)
 */
public record Alignment(int heaven, int abyss, int human) {
    public static final int BLESSING_THRESHOLD = 3;
    /** 단계별 필요 점수 (1단계, 2단계, 3단계) */
    public static final int[] TIER_THRESHOLDS = {3, 7, 12};
    public static final int MAX_TIER = TIER_THRESHOLDS.length;

    // 인덱스 = 단계 (0은 가호 없음)
    private static final double[] HEAVEN_VICTORY_HEAL_RATIO = {0.0, 0.06, 0.09, 0.12};
    // v0.9.0: 마계 쪽 선택이 시뮬레이션에서 크게 불리해(클리어율 2%) 대가를 줄이고 공격력을 올렸다
    private static final double[] ABYSS_ATTACK_MULTIPLIER = {1.0, 1.12, 1.18, 1.25};
    private static final double[] ABYSS_DAMAGE_TAKEN_MULTIPLIER = {1.0, 1.05, 1.07, 1.09};
    /** 마계의 흡혈: 준 피해 중 HP로 되돌리는 비율 (v0.9.0: 마계에는 버틸 수단이 없었다) */
    private static final double[] ABYSS_LIFESTEAL = {0.0, 0.02, 0.04, 0.05};
    private static final double[] HUMAN_GOLD_MULTIPLIER = {1.0, 1.20, 1.30, 1.40};

    /** 헌신 보상: 3단계를 넘긴 뒤 같은 세력을 한 번 더 고를 때마다 */
    public static final int DEVOTION_MAX_HP = 6;
    public static final int DEVOTION_ATTACK = 2;
    public static final int DEVOTION_GOLD = 60;

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

    /** 가호 단계 (0 = 없음, 1~3) */
    public int tier() {
        Faction dominant = dominant();
        if (dominant == null) return 0;
        int score = score(dominant);
        int tier = 0;
        for (int threshold : TIER_THRESHOLDS) {
            if (score >= threshold) tier++;
        }
        return tier;
    }

    /** 다음 단계에 필요한 점수. 가호가 없거나 이미 최고 단계면 null */
    public Integer nextTierAt() {
        int tier = tier();
        return tier == 0 || tier >= MAX_TIER ? null : TIER_THRESHOLDS[tier];
    }

    /**
     * 방금 고른 세력이 헌신 보상을 받는지. 그 세력이 단독 1위이고, 점수가 3단계 기준을 넘었을 때다.
     * 성향을 올린 뒤의 값으로 부른다(3단계 기준에 막 닿은 선택은 3단계 가호가 보상이다).
     *
     * @param chosen 방금 고른 세력
     * @return 헌신 보상 대상이면 true
     */
    public boolean isDevotion(Faction chosen) {
        return chosen != null && dominant() == chosen && score(chosen) > TIER_THRESHOLDS[MAX_TIER - 1];
    }

    /** 세력별 헌신 보상 설명 (화면의 단계표와 조우 결과 문구) */
    public static String devotionText(Faction faction) {
        return switch (faction) {
            case HEAVEN -> "최대 HP +%d".formatted(DEVOTION_MAX_HP);
            case ABYSS -> "공격력 +%d".formatted(DEVOTION_ATTACK);
            case HUMAN -> "골드 +%d".formatted(DEVOTION_GOLD);
        };
    }

    /**
     * 한 세력의 성향 점수.
     *
     * @param faction 세력
     * @return 그 세력을 택한 횟수
     */
    public int score(Faction faction) {
        return switch (faction) {
            case HEAVEN -> heaven;
            case ABYSS -> abyss;
            case HUMAN -> human;
        };
    }

    /**
     * 그 세력의 가호 단계. 단독 1위 세력이 아니면 0이다.
     *
     * @param faction 세력
     * @return 0 ~ 3
     */
    private int tierOf(Faction faction) {
        return dominant() == faction ? tier() : 0;
    }

    /** 마계의 낙인: 공격력 상승 */
    public double attackMultiplier() {
        return ABYSS_ATTACK_MULTIPLIER[tierOf(Faction.ABYSS)];
    }

    /** 마계의 낙인: 준 피해 중 HP로 되돌리는 비율 */
    public double lifestealRatio() {
        return ABYSS_LIFESTEAL[tierOf(Faction.ABYSS)];
    }

    /** 마계의 낙인의 대가: 받는 피해 증가 */
    public double damageTakenMultiplier() {
        return ABYSS_DAMAGE_TAKEN_MULTIPLIER[tierOf(Faction.ABYSS)];
    }

    /** 인간의 의지: 골드 획득 증가 */
    public double goldMultiplier() {
        return HUMAN_GOLD_MULTIPLIER[tierOf(Faction.HUMAN)];
    }

    /** 천상의 가호: 전투 승리 시 최대 HP 대비 회복 비율 */
    public double victoryHealRatio() {
        return HEAVEN_VICTORY_HEAL_RATIO[tierOf(Faction.HEAVEN)];
    }

    /** 천상 3단계: 전투 보상의 소모품 후보 +1 */
    public int bonusConsumableOptions() {
        return tierOf(Faction.HEAVEN) >= MAX_TIER ? 1 : 0;
    }

    /** 마계 3단계: 전투 보상의 유물 후보 +1 */
    public int bonusRelicOptions() {
        return tierOf(Faction.ABYSS) >= MAX_TIER ? 1 : 0;
    }

    /** 인간 3단계: 전투 보상의 스킬 후보 +1 */
    public int bonusSkillOptions() {
        return tierOf(Faction.HUMAN) >= MAX_TIER ? 1 : 0;
    }

    /** 화면과 AI 안내자에게 보여줄 가호 설명. 없으면 null */
    public String blessing() {
        Faction dominant = dominant();
        if (dominant == null) return null;
        return blessingText(dominant, tier());
    }

    /** 특정 세력의 특정 단계 가호 설명 (화면의 단계표에도 쓴다) */
    public static String blessingText(Faction faction, int tier) {
        if (tier < 1 || tier > MAX_TIER) return null;
        String stage = tier + "단계";
        return switch (faction) {
            case HEAVEN -> "천상의 가호 %s — 전투에서 승리할 때마다 최대 HP의 %d%% 회복%s".formatted(stage,
                    percent(HEAVEN_VICTORY_HEAL_RATIO[tier]), tier >= MAX_TIER ? ", 보상의 소모품 후보 +1" : "");
            case ABYSS -> "마계의 낙인 %s — 공격력 +%d%%, 준 피해의 %d%% 흡혈, 대신 받는 피해 +%d%%%s".formatted(stage,
                    percent(ABYSS_ATTACK_MULTIPLIER[tier] - 1), percent(ABYSS_LIFESTEAL[tier]),
                    percent(ABYSS_DAMAGE_TAKEN_MULTIPLIER[tier] - 1),
                    tier >= MAX_TIER ? ", 보상의 유물 후보 +1" : "");
            case HUMAN -> "인간의 의지 %s — 골드 획득 +%d%%%s".formatted(stage,
                    percent(HUMAN_GOLD_MULTIPLIER[tier] - 1), tier >= MAX_TIER ? ", 보상의 스킬 후보 +1" : "");
        };
    }

    /**
     * 비율을 반올림한 퍼센트로 바꾼다 (설명 문구용).
     *
     * @param ratio 비율 (0.12 → 12)
     * @return 퍼센트
     */
    private static int percent(double ratio) {
        return (int) Math.round(ratio * 100);
    }

    /** AI에게 넘기는 한 줄 요약 */
    public String describe() {
        Faction dominant = dominant();
        return "천상 %d / 마계 %d / 인간 %d%s".formatted(heaven, abyss, human,
                dominant == null ? " (아직 어느 쪽에도 기울지 않음)" : " (" + dominant.label() + " 쪽으로 기울었음)");
    }
}
