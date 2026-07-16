package com.renovandelve.dto.response;

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
        GameSummaryDTO summary
) {
}
