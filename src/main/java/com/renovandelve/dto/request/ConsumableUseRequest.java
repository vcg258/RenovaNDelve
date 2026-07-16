package com.renovandelve.dto.request;

/**
 * 소모품 사용 요청. inventorySlotId = game_consumable_inventory의 슬롯 ID
 */
public record ConsumableUseRequest(
        Long inventorySlotId
) {
}
