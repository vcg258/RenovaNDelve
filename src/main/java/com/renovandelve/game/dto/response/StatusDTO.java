package com.renovandelve.game.dto.response;

import com.renovandelve.game.dto.GameCharacterDTO;
import com.renovandelve.game.dto.GameConsumableInventoryDTO;
import com.renovandelve.game.dto.GameEquipmentDTO;
import com.renovandelve.game.dto.GameRelicInventoryDTO;
import com.renovandelve.game.dto.GameSkillSlotDTO;
import com.renovandelve.game.dto.GameStateDTO;
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
        List<String> skillSynergies; //발동 중인 스킬 조합 시너지 설명
        int maxSkillSlots; //스킬 슬롯 상한 (가득 차면 새 스킬은 기존 스킬과 교체)
        int minSlotsToDropBasic; //기본 공격을 버리거나 교체할 수 있는 최소 스킬 수 (화면이 같은 값을 따로 적지 않도록)
        List<String> pacts; //이번 원정에 건 서약 이름
        AlignmentDTO alignment; //성향 (천상 / 마계 / 인간)과 발동 중인 가호
        ContractDTO contract; //걸린 악마의 계약 (없으면 null)
        String kingdom; //이 원정에 고정된 왕국 정세 이름 (효과가 없는 균형이거나 예전 원정이면 null)
        List<String> kingdomEffects; //그 정세의 효과
}
