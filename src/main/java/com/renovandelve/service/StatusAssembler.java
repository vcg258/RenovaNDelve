package com.renovandelve.service;

import com.renovandelve.config.RecordMapper;
import com.renovandelve.domain.GameCharacterEntity;
import com.renovandelve.domain.GameEquipmentEntity;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.dto.*;
import com.renovandelve.dto.response.StatusDTO;
import com.renovandelve.engine.RelicEffects;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 엔티티 -> StatusDTO 수동 변환기.
 * (ModelMapper의 Lazy Proxy / 컬렉션 매핑 문제를 피하기 위해 명시적으로 변환한다)
 */
@Component
public class StatusAssembler {

    public StatusDTO toStatusDTO(GameStateEntity gameState) {
        GameStateDTO stateDTO = GameStateDTO.builder()
                .gameStateId(gameState.getGameStateId())
                .difficulty(gameState.getDifficulty())
                .isActive(gameState.getIsActive())
                .currentStage(gameState.getCurrentStage())
                .currentNodeId(gameState.getCurrentNodeId())
                .score(gameState.getScore())
                .gold(gameState.getGold())
                .startingPoints(gameState.getStartingPoints())
                .hasPhoenix(gameState.getHasPhoenix())
                .createdAt(gameState.getCreatedAt())
                .updatedAt(gameState.getUpdatedAt())
                .build();

        GameCharacterDTO characterDTO = gameState.getGameCharacter() == null ? null :
                gameState.getGameCharacter().stream().findFirst()
                        .map(this::toCharacterDTO)
                        .orElse(null);

        List<GameConsumableInventoryDTO> consumableInventoryDTO = gameState.getGameConsumableInventory() == null
                ? List.of()
                : gameState.getGameConsumableInventory().stream()
                    .filter(e -> e != null && e.getConsumable() != null)
                    .map(e -> {
                    GameConsumableInventoryDTO dto = new GameConsumableInventoryDTO();
                    dto.setSlotId(e.getSlotId());
                    dto.setConsumable(RecordMapper.map(e.getConsumable(), ConsumableDTO.class));
                    return dto;
                }).toList();

        GameEquipmentDTO equipmentDTO = toEquipmentDTO(gameState.getGameEquipment());

        List<GameRelicInventoryDTO> relicInventoryDTO = gameState.getGameRelicInventory() == null
                ? List.of()
                : gameState.getGameRelicInventory().stream()
                    .filter(e -> e != null && e.getRelic() != null)
                    .map(e -> {
                    GameRelicInventoryDTO dto = new GameRelicInventoryDTO();
                    dto.setSlotId(e.getSlotId());
                    dto.setRelic(RecordMapper.map(e.getRelic(), RelicDTO.class));
                    return dto;
                }).toList();

        List<GameSkillSlotDTO> skillSlotDTO = gameState.getGameSkillSlot() == null
                ? List.of()
                : gameState.getGameSkillSlot().stream()
                    .filter(e -> e != null && e.getSkill() != null) // 유령 슬롯(과거 데이터) 방어
                    .sorted((a, b) -> Integer.compare(a.getSlotIndex(), b.getSlotIndex()))
                    .map(e -> GameSkillSlotDTO.builder()
                            .slotId(e.getSlotId())
                            .slotIndex(e.getSlotIndex())
                            .coolTime(e.getCoolTime())
                            .skill(toSkillDTO(e.getSkill()))
                            .build())
                    .toList();

        // 발동 중인 유물 조합 시너지
        List<String> relicSynergies = RelicEffects.of(
                relicInventoryDTO.stream().map(r -> r.getRelic().name()).toList()
        ).getActiveSynergies();

        return StatusDTO.builder()
                .stateDTO(stateDTO)
                .characterDTO(characterDTO)
                .consumableInventoryDTO(consumableInventoryDTO)
                .equipmentDTO(equipmentDTO)
                .relicInventoryDTO(relicInventoryDTO)
                .skillSlotDTO(skillSlotDTO)
                .relicSynergies(relicSynergies)
                .build();
    }

    private GameCharacterDTO toCharacterDTO(GameCharacterEntity c) {
        return GameCharacterDTO.builder()
                .characterId(c.getCharacterId())
                .level(c.getLevel())
                .currentExp(c.getExp())
                .currentHp(c.getCurrentHp())
                .maxHp(c.getMaxHp())
                .attack(c.getAttack())
                .defense(c.getDefense())
                .build();
    }

    private GameEquipmentDTO toEquipmentDTO(GameEquipmentEntity equipment) {
        if (equipment == null) return null;
        GameEquipmentDTO dto = new GameEquipmentDTO();
        dto.setEquipId(equipment.getEquipId());
        if (equipment.getWeapon() != null) dto.setWeapon(RecordMapper.map(equipment.getWeapon(), WeaponDTO.class));
        if (equipment.getArmor() != null) dto.setArmor(RecordMapper.map(equipment.getArmor(), ArmorDTO.class));
        if (equipment.getHelmet() != null) dto.setHelmet(RecordMapper.map(equipment.getHelmet(), HelmetDTO.class));
        return dto;
    }

    private SkillDTO toSkillDTO(SkillEntity s) {
        return new SkillDTO(s.getName(), s.getTarget(), s.getAttackCount(), s.getCoefficient(),
                s.getCost(), s.getCooldown(), s.getStatusEffect(), s.getEffectStack(), s.getDescription());
    }
}
