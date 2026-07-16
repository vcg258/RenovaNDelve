package com.renovandelve.dto.request;

/**
 * 전투 승리 보상 선택 요청.
 * itemType = SKILL / RELIC / CONSUMABLE / SKIP
 */
public record RewardSelectRequest(
        String itemType,
        Long itemId
) {
}
