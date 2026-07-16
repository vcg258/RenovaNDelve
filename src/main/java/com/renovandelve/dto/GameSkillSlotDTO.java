package com.renovandelve.dto;

import lombok.*;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class GameSkillSlotDTO { //제외
    private Long slotId; // 슬릇 고유 ID (PK)
    private int slotIndex; // 슬릇 순번 (0 ~ 7)
    private int coolTime; // 잔여 쿨타임 0이상 값
    private GameStateDTO gameState; // 게임 세션 ID (FK)
    private SkillDTO skill; // 기술 ID (FK)
}
