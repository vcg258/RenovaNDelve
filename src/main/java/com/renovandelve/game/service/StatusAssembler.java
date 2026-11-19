package com.renovandelve.game.service;

import lombok.RequiredArgsConstructor;
import com.renovandelve.game.mapper.GameMapper;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.GameCharacterDTO;
import com.renovandelve.game.dto.GameConsumableInventoryDTO;
import com.renovandelve.game.dto.GameEquipmentDTO;
import com.renovandelve.game.dto.GameRelicInventoryDTO;
import com.renovandelve.game.dto.GameSkillSlotDTO;
import com.renovandelve.game.dto.GameStateDTO;
import com.renovandelve.game.dto.response.AlignmentDTO;
import com.renovandelve.game.dto.response.ContractDTO;
import com.renovandelve.game.dto.response.StatusDTO;
import com.renovandelve.game.engine.KingdomState;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.game.engine.RelicEffects;
import com.renovandelve.game.engine.SkillSynergies;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 게임 상태를 화면용 StatusDTO 하나로 모은다.
 * 엔티티 -> DTO 변환은 {@link GameMapper}(MapStruct)가 맡고, 여기서는 유령 행 거르기·정렬과
 * 시너지·성향·서약·계약·정세처럼 엔진이 계산하는 값을 붙인다.
 */
@Component
@RequiredArgsConstructor
public class StatusAssembler {

    private final GameMapper gameMapper;

    /**
     * 게임 상태를 화면용으로 모은다: 진행 정보, 캐릭터, 장비, 스킬 슬롯, 유물, 소모품, 시너지, 성향, 서약.
     *
     * @param gameState 게임
     * @return 화면용 상태
     */
    /** 효과가 있는 정세만 (균형이거나 예전 원정이면 null) */
    private static KingdomState activeKingdom(GameStateEntity gameState) {
        KingdomState kingdom = gameState.kingdomState();
        return kingdom == null || kingdom == KingdomState.BALANCE ? null : kingdom;
    }

    public StatusDTO toStatusDTO(GameStateEntity gameState) {
        GameStateDTO stateDTO = gameMapper.toGameStateDTO(gameState);

        GameCharacterDTO characterDTO = gameState.getGameCharacter() == null ? null :
                gameState.getGameCharacter().stream().findFirst()
                        .map(gameMapper::toCharacterDTO)
                        .orElse(null);

        List<GameConsumableInventoryDTO> consumableInventoryDTO = gameState.getGameConsumableInventory() == null
                ? List.of()
                : gameState.getGameConsumableInventory().stream()
                    .filter(e -> e != null && e.getConsumable() != null)
                    .map(gameMapper::toConsumableInventoryDTO)
                    .toList();

        GameEquipmentDTO equipmentDTO = gameMapper.toEquipmentDTO(gameState.getGameEquipment());

        List<GameRelicInventoryDTO> relicInventoryDTO = gameState.getGameRelicInventory() == null
                ? List.of()
                : gameState.getGameRelicInventory().stream()
                    .filter(e -> e != null && e.getRelic() != null)
                    .map(gameMapper::toRelicInventoryDTO)
                    .toList();

        List<GameSkillSlotDTO> skillSlotDTO = gameState.getGameSkillSlot() == null
                ? List.of()
                : gameState.getGameSkillSlot().stream()
                    .filter(e -> e != null && e.getSkill() != null) // 유령 슬롯(과거 데이터) 방어
                    .sorted((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()))
                    .map(gameMapper::toSkillSlotDTO)
                    .toList();

        // 발동 중인 유물 조합 시너지
        List<String> relicSynergies = RelicEffects.of(
                relicInventoryDTO.stream().map(r -> r.getRelic().name()).toList()
        ).getActiveSynergies();

        // 발동 중인 스킬 조합 시너지
        List<String> skillSynergies = gameState.getGameSkillSlot() == null ? List.of()
                : SkillSynergies.of(gameState.getGameSkillSlot().stream()
                    .filter(e -> e != null && e.getSkill() != null)
                    .map(e -> e.getSkill())
                    .toList()).getActiveSynergies();

        return StatusDTO.builder()
                .stateDTO(stateDTO)
                .characterDTO(characterDTO)
                .consumableInventoryDTO(consumableInventoryDTO)
                .equipmentDTO(equipmentDTO)
                .relicInventoryDTO(relicInventoryDTO)
                .skillSlotDTO(skillSlotDTO)
                .relicSynergies(relicSynergies)
                .skillSynergies(skillSynergies)
                .maxSkillSlots(SkillSlotService.MAX_SLOTS)
                .pacts(Pact.titles(Pact.parse(gameState.getPacts())))
                .alignment(AlignmentDTO.from(gameState.alignment()))
                .contract(ContractDTO.of(gameState.demonContract(), gameState.getHeavenAffinity(), gameState.getGold(),
                        gameState.getCurrentStage()))
                .kingdom(activeKingdom(gameState) == null ? null : activeKingdom(gameState).title())
                .kingdomEffects(activeKingdom(gameState) == null ? List.of() : activeKingdom(gameState).effects())
                .build();
    }
}
