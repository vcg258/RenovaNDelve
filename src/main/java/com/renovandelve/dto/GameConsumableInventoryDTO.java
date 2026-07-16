package com.renovandelve.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameConsumableInventoryDTO {
    Long slotId; // 슬롯 고유 ID
    GameStateDTO gameState; // 게임 세션 ID (FK)
    ConsumableDTO consumable; // 소모품 ID (FK)
}
