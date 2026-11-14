package com.renovandelve.engine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 서약: 플레이어가 원정 전에 스스로 거는 제약. 제약마다 점수 배율이 붙는다.
 *
 * 난이도(EASY~HELL)가 "전체를 한 번에 올리는 손잡이"라면, 서약은 어느 부분을 어렵게 할지 직접 고르는 손잡이다.
 * 익숙해진 플레이어가 같은 콘텐츠에서 새 목표를 스스로 만들 수 있고, 고른 만큼 랭킹 점수로 보상받는다.
 */
public enum Pact {
    IRON_FOE("강철의 적", "모든 적의 HP +20%", 0.15),
    FURY("들끓는 분노", "모든 적의 공격력 +15%", 0.15),
    GREED_TAX("탐욕의 세금", "상점 가격과 재입고 비용 +30%", 0.10),
    THIN_BLOOD("옅은 피", "모든 회복량이 절반", 0.15),
    WRATHFUL_LORDS("분노한 군주", "보스가 처음부터 격노 상태", 0.10);

    private final String title;
    private final String description;
    private final double scoreBonus;

    Pact(String title, String description, double scoreBonus) {
        this.title = title;
        this.description = description;
        this.scoreBonus = scoreBonus;
    }

    /**
     * 서약 이름.
     *
     * @return 이름
     */
    public String title() {
        return title;
    }

    /**
     * 서약이 거는 제약 설명.
     *
     * @return 설명
     */
    public String description() {
        return description;
    }

    /**
     * 이 서약의 점수 보너스 비율 (0.15 = +15%).
     *
     * @return 보너스 비율
     */
    public double scoreBonus() {
        return scoreBonus;
    }

    /** 요청으로 들어온 코드 목록 -> 서약 집합. 알 수 없는 코드는 거부한다 */
    public static Set<Pact> fromCodes(Collection<String> codes) {
        Set<Pact> result = new LinkedHashSet<>();
        if (codes == null) return result;
        for (String code : codes) {
            if (code == null || code.isBlank()) continue;
            try {
                result.add(valueOf(code.trim().toUpperCase()));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("알 수 없는 서약: " + code);
            }
        }
        return result;
    }

    /** DB 저장 문자열(쉼표 구분) -> 서약 집합. 저장된 값이 깨져 있어도 게임이 멈추지 않도록 모르는 값은 건너뛴다 */
    public static Set<Pact> parse(String stored) {
        Set<Pact> result = new LinkedHashSet<>();
        if (stored == null || stored.isBlank()) return result;
        for (String code : stored.split(",")) {
            Arrays.stream(values()).filter(p -> p.name().equals(code.trim())).findFirst().ifPresent(result::add);
        }
        return result;
    }

    /**
     * 서약 집합을 DB에 저장할 쉼표 구분 문자열로 바꾼다.
     *
     * @param pacts 서약 집합
     * @return 예: IRON_FOE,FURY
     */
    public static String serialize(Collection<Pact> pacts) {
        return pacts.stream().map(Enum::name).collect(Collectors.joining(","));
    }

    /** 점수 배율 = 1 + 고른 서약의 보너스 합 */
    public static double scoreMultiplier(Collection<Pact> pacts) {
        return 1.0 + pacts.stream().mapToDouble(Pact::scoreBonus).sum();
    }

    /**
     * 서약 이름 목록 (상태 바 표시용).
     *
     * @param pacts 서약 집합
     * @return 이름 목록
     */
    public static List<String> titles(Collection<Pact> pacts) {
        return new ArrayList<>(pacts.stream().map(Pact::title).toList());
    }
}
