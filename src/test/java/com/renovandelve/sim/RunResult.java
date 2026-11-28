package com.renovandelve.sim;

/** 봇 한 판의 결과 */
class RunResult {
    boolean cleared;
    int stage;
    int node;
    int level;
    int turns;
    int battles;
    int turnsTaken;
    int wraiths;
    int contracts;
    int potions;
    String error;
    /** 빈틈 찌르기와 재앙의 조율이 동시에 켜진 적이 있음 (배율이 곱으로 겹치는 경우) */
    boolean heavyAndAfflictedTogether;
    /** 원정 중 한 번이라도 켜진 스킬 조합 시너지 이름 */
    java.util.List<String> skillSynergies = java.util.List.of();
    /** 가호 3단계에 처음 닿은 노드 (지나온 노드 수, 닿지 못했으면 -1) */
    int tier3AtNode = -1;
    /** 가호 3단계에 닿은 뒤 지나간 조우 수 (성향을 더 올려도 효과가 없는 선택) */
    int encountersAfterTier3;
    /** 지나온 노드 수 */
    int nodesVisited;
    /** 이 회원이 플레이한 몇 번째 판인가 (1부터, 도감이 쌓이는 메타 성장을 보는 데 쓴다) */
    int runIndex;
    /** 보스전에 들어갈 때의 HP 비율 합과 횟수 */
    double bossEntryHp;
    int bossEntries;
    /** 마지막으로 들어간 보스전의 HP 비율 */
    double lastBossEntryHp;
    /** 일반·엘리트 전투에 들어갈 때의 HP 비율 합과 횟수 */
    double battleEntryHp;
    int battleEntries;
}
