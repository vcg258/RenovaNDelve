package com.renovandelve.game.dto.response;

import java.util.List;

/**
 * 현재 게임 전체 뷰. 이어하기 / 새로고침 시 클라이언트가 화면을 복원하는 데 사용.
 * phase = EXPLORE / BATTLE / ENCOUNTER / REWARD / SHOP / GAMEOVER / CLEAR
 */
public record GameViewDTO(
        String phase,
        StatusDTO status,
        NodeDTO node,
        RewardViewDTO reward,
        List<ShopItemDTO> shop,
        GameSummaryDTO summary,
        Integer shopRerollCost,  // 상점 단계일 때 다음 재입고 비용 (그 외 단계는 null)
        String story             // 마지막 노드의 서사. 노드가 없는 단계(전리품·상점·탐험)에서 새로고침해도 서사 칸을 복원한다
) {
}
