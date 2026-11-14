package com.renovandelve.dto.request;

/**
 * 전투 승리 보상 선택 요청.
 * itemType = SKILL / RELIC / CONSUMABLE / SKIP
 */
public record RewardSelectRequest(
        String itemType,
        Long itemId,
        Long replaceSlotId   // SKILL일 때만: 버릴 스킬 슬롯. 슬롯이 가득 찼으면 필수, 비어 있으면 생략
) {
}
