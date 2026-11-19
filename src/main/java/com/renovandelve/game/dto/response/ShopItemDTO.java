package com.renovandelve.game.dto.response;

/**
 * 상점 진열 아이템. 장비는 지금 착용 중인 것과 비교할 수 있게 보너스 두 값을 함께 보낸다.
 */
public record ShopItemDTO(
        String itemType,   // WEAPON / ARMOR / HELMET / SKILL / RELIC / CONSUMABLE
        Long itemId,
        String name,
        String description,
        int price,
        boolean sold,
        Integer bonus,        // 장비의 능력치 보너스 (장비가 아니면 null)
        Integer currentBonus  // 지금 착용 중인 같은 부위 장비의 보너스 (장비가 아니면 null, 맨손이면 0)
) {
}
