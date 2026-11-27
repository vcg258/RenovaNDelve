package com.renovandelve.game.engine;

import com.renovandelve.catalog.domain.Rarity;

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
    private Rarity rarity; // 등급 (소모품은 null)

    /** 장비의 능력치 보너스 (무기: 공격, 갑옷: 방어, 투구: 최대 HP). 장비가 아니면 null */
    private Integer bonus;
}
