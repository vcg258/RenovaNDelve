package com.renovandelve.game.engine;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 상점 재고 (Redis 저장).
 * 가격과 재고 상태를 서버가 보관하여 클라이언트 가격 조작을 차단한다.
 */
@Data
@NoArgsConstructor
public class ShopStockItem {
    private String itemType; // WEAPON / ARMOR / HELMET / SKILL / RELIC / CONSUMABLE
    private Long itemId;
    private int price;
    private String name;
    private String description;
    private boolean sold;
}
