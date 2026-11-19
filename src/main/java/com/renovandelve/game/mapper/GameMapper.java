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
import com.renovandelve.game.dto.response.BattleMonsterDTO;
import com.renovandelve.game.dto.response.ChronicleChapterDTO;
import com.renovandelve.game.dto.response.ShopItemDTO;
import com.renovandelve.game.engine.MonsterIntent;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.engine.ShopStockItem;
import com.renovandelve.global.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * 게임 한 판(진행 정보·캐릭터·장비·슬롯·인벤토리)과 연대기 엔티티, Redis 세션의 전투 몬스터·상점 진열품을 DTO로 옮긴다.
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

    /**
     * 세션의 전투 몬스터를 화면용으로 옮긴다. 점수 보상과 외치는 기술 이름은 화면에 보내지 않는다.
     *
     * @param monster 세션 몬스터
     * @return 화면용 몬스터 (예고가 비어 있으면 공격)
     */
    @Mapping(target = "intent", defaultValue = MonsterIntent.ATTACK)
    BattleMonsterDTO toBattleMonsterDTO(SessionMonster monster);

    /**
     * @param monsters 세션 몬스터
     * @return 화면용 몬스터
     */
    List<BattleMonsterDTO> toBattleMonsterDTOs(List<SessionMonster> monsters);

    /**
     * 세션의 진열품을 화면용으로 옮긴다.
     *
     * @param item         진열품
     * @param currentBonus 지금 착용 중인 같은 부위 장비의 보너스 (장비가 아니면 null)
     * @return 화면용 진열품
     */
    @Mapping(target = "currentBonus", source = "currentBonus")
    ShopItemDTO toShopItemDTO(ShopStockItem item, Integer currentBonus);
}
