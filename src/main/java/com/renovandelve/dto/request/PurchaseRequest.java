package com.renovandelve.dto.request;

/**
 * 상점 구매 요청.
 * itemType = WEAPON / ARMOR / HELMET / SKILL / RELIC / CONSUMABLE
 */
public record PurchaseRequest(
        String itemType,
        Long itemId
) {
}
