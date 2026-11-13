package com.renovandelve.dto.request;

/**
 * 소모품 사용 요청. inventorySlotId = game_consumable_inventory의 슬롯 ID
 */
public record ConsumableUseRequest(
        @jakarta.validation.constraints.NotNull(message = "사용할 소모품을 선택해주세요.")
        Long inventorySlotId
) {
}
