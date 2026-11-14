package com.renovandelve.game.engine;

/**
 * 왕국의 저울: 모든 플레이어의 선택이 쌓여 정해지는 왕국의 정세.
 *
 * 지난 7일 동안 원정자들이 조우에서 택한 방식(성향)을 회원마다 비율로 바꿔 더한다(한 사람이 한 표).
 * 한 세력이 40% 이상이고 2위보다 10%p 이상 앞서면 그 세력의 정세, 아니면 균형이다.
 * 정세는 원정을 시작할 때 고정되고, 그 원정의 모든 전투·보상에 약하게 작용한다.
 */
public enum KingdomState {
    HEAVEN_RISING("정화의 불길",
            "원정자들이 천상의 방식에 기울었다. 정화의 불이 왕국 곳곳에서 마계의 것들을 태우고, 대신 헌물을 요구한다.",
            0.9, 1.0, 0.9, 1.0, "적 HP -10%", "전투 골드 -10%"),
    ABYSS_WIDENING("넓어지는 문",
            "원정자들이 마계의 손을 잡았다. 문이 넓어져 더 사나운 것들이 올라오지만, 금화도 함께 흘러나온다.",
            1.0, 1.1, 1.2, 1.0, "적 공격력 +10%", "전투 골드 +20%"),
    HUMAN_RESOLVE("다시 쌓는 성벽",
            "원정자들이 누구에게도 기대지 않았다. 살아남은 자들이 성벽을 다시 쌓고, 싸움의 요령을 서로 가르친다.",
            1.0, 1.0, 1.0, 1.15, "경험치 +15%", null),
    BALANCE("흔들리는 저울",
            "천상도 마계도 인간도 왕국을 쥐지 못했다. 저울은 아직 어느 쪽으로도 기울지 않았다.",
            1.0, 1.0, 1.0, 1.0, null, null);

    /** 한 세력이 정세를 쥐는 최소 비율 */
    public static final double DOMINANT_SHARE = 0.40;
    /** 2위와 벌어져야 하는 차이 */
    public static final double LEAD_MARGIN = 0.10;

    private final String title;
    private final String description;
    private final double enemyHpMultiplier;
    private final double enemyAttackMultiplier;
    private final double goldMultiplier;
    private final double expMultiplier;
    private final String effect1;
    private final String effect2;

    KingdomState(String title, String description, double enemyHpMultiplier, double enemyAttackMultiplier,
                 double goldMultiplier, double expMultiplier, String effect1, String effect2) {
        this.title = title;
        this.description = description;
        this.enemyHpMultiplier = enemyHpMultiplier;
        this.enemyAttackMultiplier = enemyAttackMultiplier;
        this.goldMultiplier = goldMultiplier;
        this.expMultiplier = expMultiplier;
        this.effect1 = effect1;
        this.effect2 = effect2;
    }

    /** @return 정세 이름 */
    public String title() { return title; }

    /** @return 정세 설명 */
    public String description() { return description; }

    /** @return 적 HP 배율 */
    public double enemyHpMultiplier() { return enemyHpMultiplier; }

    /** @return 적 공격력 배율 */
    public double enemyAttackMultiplier() { return enemyAttackMultiplier; }

    /** @return 전투 골드 배율 */
    public double goldMultiplier() { return goldMultiplier; }

    /** @return 경험치 배율 */
    public double expMultiplier() { return expMultiplier; }

    /** @return 효과 설명 목록 (없으면 비어 있음) */
    public java.util.List<String> effects() {
        return java.util.stream.Stream.of(effect1, effect2).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * 저장된 이름을 읽는다.
     *
     * @param value 이름
     * @return 정세, 없거나 모르는 값이면 null (효과 없음)
     */
    public static KingdomState parse(String value) {
        if (value == null) return null;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /**
     * 세 세력의 비율로 정세를 정한다.
     *
     * @param heaven 천상 비율 (0~1)
     * @param abyss  마계 비율
     * @param human  인간 비율
     * @return 정세
     */
    public static KingdomState decide(double heaven, double abyss, double human) {
        double[] shares = {heaven, abyss, human};
        int top = 0;
        for (int i = 1; i < 3; i++) if (shares[i] > shares[top]) top = i;
        double second = 0;
        for (int i = 0; i < 3; i++) if (i != top) second = Math.max(second, shares[i]);
        // 비율은 나눗셈 결과라 0.5 - 0.4가 0.0999...가 된다. 경계값을 포함하도록 아주 작은 여유를 둔다
        double epsilon = 1e-9;
        if (shares[top] < DOMINANT_SHARE - epsilon || shares[top] - second < LEAD_MARGIN - epsilon) return BALANCE;
        return switch (top) {
            case 0 -> HEAVEN_RISING;
            case 1 -> ABYSS_WIDENING;
            default -> HUMAN_RESOLVE;
        };
    }

    /** @return 이 정세를 서사에 남기는 한 줄 (AI 대화 기록용) */
    public String chronicleLine() {
        return "[왕국의 정세] %s — %s".formatted(title, description);
    }
}
