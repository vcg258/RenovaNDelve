package com.renovandelve.sim;

/**
 * 봇의 성격. 사람보다 단순하게 플레이하므로, 실제 사람의 클리어율은 신중형보다 조금 높을 것으로 본다.
 * 아래쪽 *_ONLY 봇은 탐욕형의 행동을 하나씩만 켠 것이다 (어느 행동이 손해인지 가르는 데 쓴다).
 */
enum BotPolicy {
    /** 안전한 길, 천상의 선택, 계약 거절, 위험하면 일찍 방어 */
    CAUTIOUS("HEAVEN", 0.35, 0.6, false, false, false),
    /** 평범한 길, 인간의 선택, 계약 거절, 체력이 낮으면 은밀한 길 */
    BALANCED("HUMAN", 0.5, 0.5, false, false, true),
    /** 위험한 길, 마계의 선택, 계약 서명, 방어는 거의 하지 않음 */
    GREEDY("ABYSS", 0.8, 0.4, true, true, false),
    /** 신중형 + 위험한 길만 */
    DANGER_ONLY("HEAVEN", 0.35, 0.6, true, false, false),
    /** 신중형 + 마계 선택만 */
    ABYSS_ONLY("ABYSS", 0.35, 0.6, false, false, false),
    /** 신중형 + 계약 서명만 */
    CONTRACT_ONLY("HEAVEN", 0.35, 0.6, false, true, false),
    /** 신중형 + 방어를 거의 하지 않음 */
    RECKLESS_ONLY("HEAVEN", 0.8, 0.6, false, false, false);

    /** 조우에서 고르는 세력 */
    final String faction;
    /** 이번 턴 예상 피해가 현재 HP의 이 비율 이상이면 방어 */
    final double guardIfIncomingOver;
    /** 모닥불에서 HP가 이 비율 아래면 휴식, 아니면 단련 */
    final double restHealBelow;
    /** 체력이 넉넉하면 위험한 길 */
    final boolean dangerPath;
    /** 계약서에 서명 */
    final boolean signContracts;
    /** 체력이 낮으면 은밀한 길 */
    final boolean ambushWhenHurt;

    BotPolicy(String faction, double guardIfIncomingOver, double restHealBelow,
              boolean dangerPath, boolean signContracts, boolean ambushWhenHurt) {
        this.faction = faction;
        this.guardIfIncomingOver = guardIfIncomingOver;
        this.restHealBelow = restHealBelow;
        this.dangerPath = dangerPath;
        this.signContracts = signContracts;
        this.ambushWhenHurt = ambushWhenHurt;
    }
}
