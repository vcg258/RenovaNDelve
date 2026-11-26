package com.renovandelve.sim;

import java.util.List;
import java.util.Objects;

/** 난이도·봇 조합 하나의 결과 요약 (보고서 한 줄) */
record Summary(String key, List<RunResult> runs) {

    static final String HEADER = """
            | 난이도/봇 | 판 | 클리어율 | 평균 도달 스테이지 | 사망 스테이지 1/2/3/4/5 | 보스에서 사망 | 평균 레벨 | 전투당 턴 | 망령 조우 | 계약 서명 | 오류 |
            |---|---|---|---|---|---|---|---|---|---|---|
            """;

    static Summary of(String key, List<RunResult> runs) {
        return new Summary(key, runs);
    }

    String row() {
        List<RunResult> ok = runs.stream().filter(r -> r.error == null).toList();
        int n = ok.size();
        long clears = ok.stream().filter(r -> r.cleared).count();
        int[] deaths = new int[6];
        long bossDeaths = 0;
        for (RunResult r : ok) {
            if (r.cleared) continue;
            deaths[Math.max(1, Math.min(5, r.stage))]++;
            if (r.node >= 10) bossDeaths++;
        }
        long losses = n - clears;
        double avgStage = ok.stream().mapToInt(r -> r.stage).average().orElse(0);
        double avgLevel = ok.stream().mapToInt(r -> r.level).average().orElse(0);
        int battles = ok.stream().mapToInt(r -> r.battles).sum();
        int turns = ok.stream().mapToInt(r -> r.turns).sum();
        String firstError = runs.stream().map(r -> r.error).filter(Objects::nonNull).findFirst().orElse("");
        return "| %s | %d | **%.0f%%** | %.2f | %d/%d/%d/%d/%d | %s | %.1f | %.1f | %d | %d | %d %s |".formatted(
                key, n, n == 0 ? 0 : 100.0 * clears / n, avgStage,
                deaths[1], deaths[2], deaths[3], deaths[4], deaths[5],
                losses == 0 ? "-" : "%.0f%%".formatted(100.0 * bossDeaths / losses),
                avgLevel, battles == 0 ? 0 : (double) turns / battles,
                ok.stream().mapToInt(r -> r.wraiths).sum(), ok.stream().mapToInt(r -> r.contracts).sum(),
                runs.size() - n, firstError.length() > 60 ? firstError.substring(0, 60) : firstError);
    }

    static final String EXTRA_HEADER = """
            | 난이도/봇 | 판 | 켜 본 스킬 시너지 수 | 빈틈 찌르기 + 재앙의 조율 | 시너지별 보유율 | 가호 3단계 도달 | 도달 노드(평균) | 지나온 노드(평균) | 3단계 뒤 조우(평균) |
            |---|---|---|---|---|---|---|---|---|
            """;

    private static final List<String> SYNERGIES = List.of("피의 연계", "번지는 불길", "상처에 스민 독", "휩쓸기", "빈틈 찌르기", "재앙의 조율");

    /** 스킬 조합과 가호 단계 지표 */
    String extraRow() {
        List<RunResult> ok = runs.stream().filter(r -> r.error == null).toList();
        int n = Math.max(1, ok.size());
        double avgSynergies = ok.stream().mapToInt(r -> r.skillSynergies.size()).average().orElse(0);
        long both = ok.stream().filter(r -> r.heavyAndAfflictedTogether).count();
        StringBuilder each = new StringBuilder();
        for (String name : SYNERGIES) {
            long c = ok.stream().filter(r -> r.skillSynergies.contains(name)).count();
            if (!each.isEmpty()) each.append(", ");
            each.append("%s %.0f%%".formatted(name, 100.0 * c / n));
        }
        List<RunResult> reached = ok.stream().filter(r -> r.tier3AtNode >= 0).toList();
        return "| %s | %d | %.2f | %.0f%% | %s | %.0f%% | %.1f | %.1f | %.1f |".formatted(
                key, ok.size(), avgSynergies, 100.0 * both / n, each,
                100.0 * reached.size() / n,
                reached.stream().mapToInt(r -> r.tier3AtNode).average().orElse(0),
                ok.stream().mapToInt(r -> r.nodesVisited).average().orElse(0),
                reached.stream().mapToInt(r -> r.encountersAfterTier3).average().orElse(0));
    }
}
