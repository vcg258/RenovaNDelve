package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.engine.ShopStockItem;

import java.util.List;

public interface ShopService {

    /**
     * 현재 게임 상태 기반 상점 재고 생성 (노드당 1회, 세션에 저장됨)
     */
    List<ShopStockItem> generateStock(GameStateEntity gameState);

    /**
     * 상점 아이템 구매 적용. 골드 차감 + 장착/인벤토리 반영 + 도감 등록.
     * 가격/재고 검증은 세션 재고(stockItem) 기준으로 이미 끝난 상태로 호출된다.
     */
    void applyPurchase(GameStateEntity gameState, MemberEntity member, ShopStockItem stockItem);
}
