package com.renovandelve.game.engine;

import com.renovandelve.catalog.domain.Rarity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class RarityTableTest {

    private record Item(String name, Rarity rarity) {
    }

    private static Map<Rarity, Double> shares(int tier, int rolls) {
        Random random = new Random(42);
        Map<Rarity, Integer> counts = new EnumMap<>(Rarity.class);
        for (int i = 0; i < rolls; i++) counts.merge(RarityTable.roll(tier, random), 1, Integer::sum);
        Map<Rarity, Double> shares = new EnumMap<>(Rarity.class);
        counts.forEach((r, c) -> shares.put(r, c / (double) rolls));
        return shares;
    }

    @Test
    @DisplayName("굴린 등급의 비율이 표의 가중치를 따른다")
    void rollFollowsWeights() {
        Map<Rarity, Double> first = shares(1, 100_000);
        assertThat(first.get(Rarity.COMMON)).isCloseTo(0.70, within(0.01));
        assertThat(first.get(Rarity.EPIC)).isCloseTo(0.05, within(0.01));
        Map<Rarity, Double> last = shares(5, 100_000);
        assertThat(last.get(Rarity.EPIC)).isCloseTo(0.30, within(0.01));
    }

    @Test
    @DisplayName("깊을수록 영웅 비중이 커지고, 표를 벗어난 단계는 가까운 끝으로 맞춘다")
    void deeperIsRicher() {
        double previous = -1;
        for (int tier = 1; tier <= RarityTable.maxTier(); tier++) {
            int[] w = RarityTable.WEIGHTS[tier - 1];
            double epic = w[2] / (double) (w[0] + w[1] + w[2]);
            assertThat(epic).isGreaterThan(previous);
            previous = epic;
        }
        assertThat(shares(0, 10_000).get(Rarity.COMMON)).isCloseTo(0.70, within(0.02));
        assertThat(shares(99, 10_000).get(Rarity.EPIC)).isCloseTo(0.40, within(0.02));
    }

    @Test
    @DisplayName("겹치지 않게 고르고, 후보가 모자라면 있는 만큼만")
    void pickWithoutDuplicates() {
        List<Item> pool = IntStream.range(0, 6).mapToObj(i -> new Item("i" + i, Rarity.values()[i % 3])).toList();
        List<Item> picked = RarityTable.pick(pool, Item::rarity, 4, 3, new Random(1));
        assertThat(picked).hasSize(4).doesNotHaveDuplicates();
        assertThat(RarityTable.pick(pool, Item::rarity, 10, 3, new Random(1))).hasSize(6);
        assertThat(RarityTable.pick(List.<Item>of(), Item::rarity, 2, 3, new Random(1))).isEmpty();
    }

    @Test
    @DisplayName("원하는 등급이 떨어지면 한 단계 아래부터 찾는다")
    void fallsBackDownFirst() {
        assertThat(RarityTable.fallbackOrder(Rarity.EPIC)).containsExactly(Rarity.EPIC, Rarity.RARE, Rarity.COMMON);
        assertThat(RarityTable.fallbackOrder(Rarity.RARE)).containsExactly(Rarity.RARE, Rarity.COMMON, Rarity.EPIC);
        // 흔함만 있으면 어떤 등급을 굴려도 흔함이 나온다
        List<Item> commons = List.of(new Item("a", Rarity.COMMON), new Item("b", Rarity.COMMON));
        assertThat(RarityTable.pick(commons, Item::rarity, 2, 7, new Random(3))).hasSize(2);
    }

    @Test
    @DisplayName("초반에는 영웅 항목이 드물다: 스테이지 1에서 영웅 하나와 흔함 여럿이면 영웅이 뽑히는 비율은 5% 안팎")
    void epicIsRareEarly() {
        List<Item> pool = List.of(new Item("epic", Rarity.EPIC), new Item("c1", Rarity.COMMON), new Item("c2", Rarity.COMMON),
                new Item("r1", Rarity.RARE));
        Random random = new Random(7);
        int epic = 0, trials = 20_000;
        for (int i = 0; i < trials; i++) {
            if (RarityTable.pick(pool, Item::rarity, 1, 1, random).get(0).rarity() == Rarity.EPIC) epic++;
        }
        assertThat(epic / (double) trials).isCloseTo(0.05, within(0.01));
    }

    @Test
    @DisplayName("등급이 비어 있는 항목은 흔함으로 보고 고른다 (멈추지 않는다)")
    void nullRarityCountsAsCommon() {
        List<Item> pool = List.of(new Item("a", null), new Item("b", null));
        assertThat(RarityTable.pick(pool, Item::rarity, 2, 5, new Random(9))).hasSize(2);
    }
}
