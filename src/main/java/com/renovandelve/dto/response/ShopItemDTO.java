package com.renovandelve.dto.response;

/**
 * 상점 진열 아이템.
 */
public record ShopItemDTO(
        String itemType,   // WEAPON / ARMOR / HELMET / SKILL / RELIC / CONSUMABLE
        Long itemId,
        String name,
        String description,
        int price,
        boolean sold
) {
}
