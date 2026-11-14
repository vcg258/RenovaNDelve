package com.renovandelve.game.dto;

import lombok.*;

import java.util.List;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class GameCharacterDTO { //제외
    private Long characterId; // 슬릇 고유 ID
    private int level; // 캐릭터 레벨
    private int currentExp; //현재 경험치
    private int currentHp; // 현재 HP
    private int maxHp; // 최대 HP (레벨당 10 + 보너스)
    private int attack; // 캐릭터 공격력 (레벨당 1 + 보너스)
    private int defense; // 캐릭터 방어력 (레벨당 1 + 보너스)
    private GameStateDTO gameState; // 게임 세션 ID (FK)
    private List<BuffDTO> buffDTOS; //캐릭터가 보유한 버프, 디버프
}
