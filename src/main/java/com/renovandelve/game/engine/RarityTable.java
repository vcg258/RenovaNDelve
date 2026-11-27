package com.renovandelve.game.engine;

import com.renovandelve.catalog.domain.Rarity;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Function;

/**
 * 등급별 등장 확률. 상점 진열품과 전리품 후보는 등급을 먼저 굴리고, 그 등급 안에서 항목을 고른다.
 *
 * 깊이 내려갈수록 희귀·영웅의 비중이 커진다. 강한 적을 이긴 전리품은 더 깊은 스테이지의 확률을 쓴다
 * (엘리트·망령 +1, 보스 +2). 그 등급에 남은 항목이 없으면 가까운 등급(아래 먼저)에서 고른다.
 */
public final class RarityTable {

    /** [단계 - 1][COMMON, RARE, EPIC] 가중치. 마지막 단계는 스테이지 5 보스의 전리품용 */
    static final int[][] WEIGHTS = {
            {70, 25, 5},
            {60, 30, 10},
            {50, 35, 15},
            {40, 38, 22},
            {30, 40, 30},
            {25, 40, 35},
            {20, 40, 40},
    };

    private RarityTable() {
    }

    /** @return 쓸 수 있는 가장 깊은 단계 */
    public static int maxTier() {
        return WEIGHTS.length;
    }

    /**
     * @param tier   단계 (스테이지, 강한 적의 전리품이면 그보다 깊다). 범위를 벗어나면 가까운 끝으로 맞춘다
     * @param random 난수
     * @return 굴린 등급
     */
    public static Rarity roll(int tier, Random random) {
        int[] w = WEIGHTS[Math.max(1, Math.min(WEIGHTS.length, tier)) - 1];
        int r = random.nextInt(w[0] + w[1] + w[2]);
        if (r < w[0]) return Rarity.COMMON;
        if (r < w[0] + w[1]) return Rarity.RARE;
        return Rarity.EPIC;
    }

    /**
     * 후보에서 겹치지 않게 count개를 고른다. 하나 고를 때마다 등급을 새로 굴린다.
     *
     * @param pool     후보
     * @param rarityOf 항목의 등급
     * @param count    고를 수
     * @param tier     단계
     * @param random   난수
     * @param <T>      항목 타입
     * @return 고른 항목 (후보가 적으면 전부)
     */
    public static <T> List<T> pick(List<T> pool, Function<T, Rarity> rarityOf, int count, int tier, Random random) {
        List<T> remaining = new ArrayList<>(pool);
        List<T> picked = new ArrayList<>();
        while (picked.size() < count && !remaining.isEmpty()) {
            Rarity wanted = roll(tier, random);
            for (Rarity rarity : fallbackOrder(wanted)) {
                List<T> candidates = remaining.stream().filter(item -> rarityOrCommon(rarityOf.apply(item)) == rarity).toList();
                if (candidates.isEmpty()) continue;
                T item = candidates.get(random.nextInt(candidates.size()));
                picked.add(item);
                remaining.remove(item);
                break;
            }
        }
        return picked;
    }

    /** 등급이 비어 있으면 흔함으로 본다 (모든 항목이 세 등급 중 하나에 들어가 반복이 반드시 끝난다) */
    private static Rarity rarityOrCommon(Rarity rarity) {
        return rarity == null ? Rarity.COMMON : rarity;
    }

    /** 원하는 등급이 다 떨어졌을 때 찾는 순서: 원하는 등급 -> 한 단계 아래 -> 위 */
    static List<Rarity> fallbackOrder(Rarity wanted) {
        return switch (wanted) {
            case COMMON -> List.of(Rarity.COMMON, Rarity.RARE, Rarity.EPIC);
            case RARE -> List.of(Rarity.RARE, Rarity.COMMON, Rarity.EPIC);
            case EPIC -> List.of(Rarity.EPIC, Rarity.RARE, Rarity.COMMON);
        };
    }
}
