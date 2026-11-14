package com.renovandelve.game.engine;

/**
 * 악마의 계약서. 마계가 지금 당장의 혜택을 주고, 대가는 숨겨진 조항으로 나중에 받아 간다.
 *
 * 조항은 서버만 안다. 화면에는 조항의 종류에서 나온 "작은 글씨" 힌트만 보낸다(조우 선택지의 숨겨진 결과와 같은 구조).
 * 힌트 없이 대가만 숨기면 함정이 되므로, 무엇이 언제 올지는 힌트로 짐작할 수 있게 한다.
 * 한 원정에 계약은 하나만 걸려 있을 수 있고, 조항이 발동하면 계약은 끝난다.
 *
 * 저장 형식은 game_state.contract의 "BOON:CLAUSE:남은횟수" 한 줄이다.
 *
 * @param boon      받은 혜택
 * @param clause    숨겨진 조항
 * @param remaining 조항이 발동하기까지 남은 횟수 (조항마다 세는 대상이 다르다)
 */
public record DemonContract(Boon boon, Clause clause, int remaining) {

    /** 계약을 제안받을 확률 (조우 노드에서, 걸린 계약이 없을 때) */
    public static final double OFFER_CHANCE = 0.25;

    /** 지금 받는 혜택 */
    public enum Boon {
        GOLD("황금", "골드"),
        POWER("힘", "공격력 +4 (영구)"),
        VIGOR("생기", "최대 HP +20, HP 20 회복"),
        RELIC("유물", "유물 하나");

        private final String title;
        private final String summary;

        Boon(String title, String summary) {
            this.title = title;
            this.summary = summary;
        }

        /** @return 혜택 이름 */
        public String title() { return title; }

        /** @return 혜택 설명 */
        public String summary() { return summary; }

        /**
         * 황금의 골드 양. 깊을수록 많다.
         *
         * @param stage 스테이지
         * @return 골드
         */
        public static int goldAmount(int stage) {
            return 100 + 60 * stage;
        }
    }

    /** 숨겨진 조항 */
    public enum Clause {
        /** 다음 보스전이 시작될 때 지금 HP의 30%를 잃는다 */
        BLOOD_TITHE("왕좌 앞에 서는 날, 피로 값을 치른다.", 1),
        /** 세 노드를 더 나아가면 골드의 60%를 잃는다 */
        GOLD_DEBT("세 걸음 뒤, 빚을 받으러 온다.", 3),
        /** 다음 전투에서 이겨도 전리품을 고를 수 없다 */
        SPOILS_CLAIM("다음 전리품은 그의 몫이다.", 1),
        /** 다음 두 번의 전투에서 적의 공격력이 25% 오른다 */
        SHADOW_PACT("두 번의 싸움 동안 그림자가 적의 편에 선다.", 2);

        private final String hint;
        private final int count;

        Clause(String hint, int count) {
            this.hint = hint;
            this.count = count;
        }

        /** @return 서명 전에 보여 주는 작은 글씨 */
        public String hint() { return hint; }

        /** @return 처음 남은 횟수 */
        public int count() { return count; }
    }

    /** 그림자 조항이 적에게 더하는 공격력 배율 */
    public static final double SHADOW_ATTACK_MULTIPLIER = 1.25;
    /** 피의 십일조가 가져가는 현재 HP 비율 */
    public static final double BLOOD_TITHE_RATIO = 0.3;
    /** 빚이 가져가는 골드 비율 */
    public static final double GOLD_DEBT_RATIO = 0.6;

    /**
     * 새 계약.
     *
     * @param boon   혜택
     * @param clause 조항
     * @return 남은 횟수가 조항의 처음 값인 계약
     */
    public static DemonContract of(Boon boon, Clause clause) {
        return new DemonContract(boon, clause, clause.count());
    }

    /**
     * 저장된 한 줄을 읽는다.
     *
     * @param value "BOON:CLAUSE:남은횟수"
     * @return 계약, 없거나 형식이 틀리면 null
     */
    public static DemonContract parse(String value) {
        if (value == null || value.isBlank()) return null;
        String[] parts = value.split(":");
        if (parts.length != 3) return null;
        try {
            return new DemonContract(Boon.valueOf(parts[0]), Clause.valueOf(parts[1]), Integer.parseInt(parts[2]));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** @return 저장용 한 줄 */
    public String serialize() {
        return boon.name() + ":" + clause.name() + ":" + remaining;
    }

    /**
     * 횟수를 하나 센다.
     *
     * @return 하나 줄어든 계약
     */
    public DemonContract tick() {
        return new DemonContract(boon, clause, Math.max(0, remaining - 1));
    }

    /** @return 이번에 센 것으로 조항이 다 찼으면 true */
    public boolean due() {
        return remaining <= 0;
    }

    /**
     * 계약 파기 비용 (천상 성향이 충분할 때만 가능). 깊을수록 비싸다.
     *
     * @param stage 스테이지
     * @return 골드
     */
    public static int breakCost(int stage) {
        return 80 * stage;
    }

    /** 파기할 수 있는 천상 성향 */
    public static final int BREAK_HEAVEN_AFFINITY = 3;
}
