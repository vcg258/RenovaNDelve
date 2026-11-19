package com.renovandelve.game.mapper;

import com.renovandelve.catalog.mapper.CatalogMapper;
import com.renovandelve.game.domain.ChronicleChapterEntity;
import com.renovandelve.game.domain.GameCharacterEntity;
import com.renovandelve.game.domain.GameConsumableInventoryEntity;
import com.renovandelve.game.domain.GameEquipmentEntity;
import com.renovandelve.game.domain.GameRelicInventoryEntity;
import com.renovandelve.game.domain.GameSkillSlotEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.GameCharacterDTO;
import com.renovandelve.game.dto.GameConsumableInventoryDTO;
import com.renovandelve.game.dto.GameEquipmentDTO;
import com.renovandelve.game.dto.GameRelicInventoryDTO;
import com.renovandelve.game.dto.GameSkillSlotDTO;
import com.renovandelve.game.dto.GameStateDTO;
import com.renovandelve.game.dto.response.ChronicleChapterDTO;
import com.renovandelve.global.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * 게임 한 판(진행 정보·캐릭터·장비·슬롯·인벤토리)과 연대기 엔티티를 DTO로 옮긴다.
 * 장비·스킬·유물·소모품 자체는 {@link CatalogMapper}가 옮긴다.
 * DTO의 역참조 필드(gameState, member)는 순환을 막기 위해 비운다.
 */
@Mapper(config = MapStructConfig.class, uses = CatalogMapper.class)
public interface GameMapper {

    /**
     * @param gameState 게임
     * @return 진행 정보 DTO (회원은 비움)
     */
    @Mapping(target = "member", ignore = true)
    GameStateDTO toGameStateDTO(GameStateEntity gameState);

    /**
     * @param character 캐릭터
     * @return DTO (버프는 세션에 있어 비움)
     */
    @Mapping(target = "currentExp", source = "exp")
    @Mapping(target = "gameState", ignore = true)
    @Mapping(target = "buffDTOS", ignore = true)
    GameCharacterDTO toCharacterDTO(GameCharacterEntity character);

    /**
     * @param equipment 장비 슬롯 (없으면 null)
     * @return DTO, 비어 있는 부위는 null
     */
    @Mapping(target = "gameState", ignore = true)
    GameEquipmentDTO toEquipmentDTO(GameEquipmentEntity equipment);

    /**
     * @param slot 스킬 슬롯
     * @return DTO
     */
    @Mapping(target = "gameState", ignore = true)
    GameSkillSlotDTO toSkillSlotDTO(GameSkillSlotEntity slot);

    /**
     * @param slot 유물 칸
     * @return DTO
     */
    @Mapping(target = "gameState", ignore = true)
    GameRelicInventoryDTO toRelicInventoryDTO(GameRelicInventoryEntity slot);

    /**
     * @param slot 소모품 칸
     * @return DTO
     */
    @Mapping(target = "gameState", ignore = true)
    GameConsumableInventoryDTO toConsumableInventoryDTO(GameConsumableInventoryEntity slot);

    /**
     * @param chapter 연대기의 한 장
     * @return DTO
     */
    ChronicleChapterDTO toChronicleChapterDTO(ChronicleChapterEntity chapter);
}
