package com.renovandelve.game.dto;

import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.catalog.dto.HelmetDTO;
import com.renovandelve.catalog.dto.WeaponDTO;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameEquipmentDTO{
    Long equipId; // 슬릇 고유 ID
    GameStateDTO gameState; // 게임 세션 ID (FK)
    WeaponDTO weapon; // 장착 중인 무기 (FK)
    ArmorDTO armor; // 장착 중인 갑옷 (FK)
    HelmetDTO helmet; // 장착 중인 투구 (FK)
}
