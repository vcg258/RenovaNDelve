package com.renovandelve.game.service;

import com.renovandelve.global.config.RecordMapper;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.catalog.dto.ConsumableDTO;
import com.renovandelve.game.dto.GameCharacterDTO;
import com.renovandelve.game.dto.GameConsumableInventoryDTO;
import com.renovandelve.game.dto.GameEquipmentDTO;
import com.renovandelve.game.dto.GameRelicInventoryDTO;
import com.renovandelve.game.dto.GameSkillSlotDTO;
import com.renovandelve.game.dto.GameStateDTO;
import com.renovandelve.catalog.dto.HelmetDTO;
import com.renovandelve.catalog.dto.RelicDTO;
import com.renovandelve.catalog.dto.SkillDTO;
import com.renovandelve.catalog.dto.WeaponDTO;
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
 * 엔티티 -> StatusDTO 수동 변환기.
 * (ModelMapper의 Lazy Proxy / 컬렉션 매핑 문제를 피하기 위해 명시적으로 변환한다)
 */
@Component
public class StatusAssembler {

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

    /**
     * 캐릭터를 화면용으로 옮긴다.
     *
     * @param c 캐릭터
     * @return DTO
     */
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

    /**
     * 장비 슬롯을 화면용으로 옮긴다.
     *
     * @param equipment 장비 슬롯 (없으면 null)
     * @return DTO
     */
    private GameEquipmentDTO toEquipmentDTO(GameEquipmentEntity equipment) {
        if (equipment == null) return null;
        GameEquipmentDTO dto = new GameEquipmentDTO();
        dto.setEquipId(equipment.getEquipId());
        if (equipment.getWeapon() != null) dto.setWeapon(RecordMapper.map(equipment.getWeapon(), WeaponDTO.class));
        if (equipment.getArmor() != null) dto.setArmor(RecordMapper.map(equipment.getArmor(), ArmorDTO.class));
        if (equipment.getHelmet() != null) dto.setHelmet(RecordMapper.map(equipment.getHelmet(), HelmetDTO.class));
        return dto;
    }

    /**
     * 스킬 엔티티를 응답용 DTO로 옮긴다.
     *
     * @param s 스킬
     * @return DTO
     */
    private SkillDTO toSkillDTO(SkillEntity s) {
        return new SkillDTO(s.getName(), s.getTarget(), s.getAttackCount(), s.getCoefficient(),
                s.getCost(), s.getCooldown(), s.getStatusEffect(), s.getEffectStack(), s.getDescription());
    }
}
