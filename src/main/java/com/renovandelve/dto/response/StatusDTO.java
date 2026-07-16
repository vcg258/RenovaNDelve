package com.renovandelve.dto.response;

import com.renovandelve.dto.*;
import lombok.*;

import java.util.List;

@ToString
@AllArgsConstructor
@NoArgsConstructor
@Data
@Builder
//이어하기, 노드 진행, 전투시 사용
public class StatusDTO {
        GameStateDTO stateDTO; //게임 세션의 정보
        GameCharacterDTO characterDTO; // 캐릭터의 정보
        List<GameConsumableInventoryDTO> consumableInventoryDTO; //소모품 인벤토리
        GameEquipmentDTO equipmentDTO; //착용 장비
        List<GameRelicInventoryDTO> relicInventoryDTO; //보유 유물
        List<GameSkillSlotDTO> skillSlotDTO; //보유 스킬
        List<String> relicSynergies; //발동 중인 유물 조합 시너지 설명
}
