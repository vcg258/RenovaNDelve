package com.renovandelve.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameRelicInventoryDTO {
    Long slotId; // 슬릇 고유 ID
    GameStateDTO gameState; // 게임 세션 ID (FK)
    RelicDTO relic; // 유물 ID (FK)
}
