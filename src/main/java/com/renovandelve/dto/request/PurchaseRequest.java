package com.renovandelve.dto.request;

/**
 * 상점 구매 요청.
 * itemType = WEAPON / ARMOR / HELMET / SKILL / RELIC / CONSUMABLE
 */
public record PurchaseRequest(
        @jakarta.validation.constraints.NotBlank(message = "구매할 상품을 선택해주세요.")
        String itemType,

        @jakarta.validation.constraints.NotNull(message = "구매할 상품을 선택해주세요.")
        Long itemId
) {
}
