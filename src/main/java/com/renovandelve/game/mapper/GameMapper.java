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
import com.renovandelve.game.domain.WraithEntity;
import com.renovandelve.game.dto.response.AlignmentDTO;
import com.renovandelve.game.dto.response.ContractDTO;
import com.renovandelve.game.dto.response.GameHistoryDTO;
import com.renovandelve.game.dto.response.PactDTO;
import com.renovandelve.game.dto.response.RunStatsDTO;
import com.renovandelve.game.dto.response.WraithDTO;
import com.renovandelve.game.engine.Alignment;
import com.renovandelve.game.engine.DemonContract;
import com.renovandelve.game.engine.Faction;
import com.renovandelve.game.engine.Pact;
import com.renovandelve.game.engine.RunStats;
import com.renovandelve.game.engine.MonsterIntent;
import com.renovandelve.game.engine.SessionMonster;
import com.renovandelve.game.engine.ShopStockItem;
import com.renovandelve.global.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

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
    @Mapping(target = "expToNext", expression = "java(character.expToNextLevel())")
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
    @Mapping(target = "intentAtk", expression = "java(com.renovandelve.game.engine.MonsterIntent.expectedAttack(monster.getIntent(), monster.getAtk()))")
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

    /**
     * 모험 기록 한 줄. 진행 중이면 결과는 PLAYING, 끝난 시각은 비운다.
     *
     * @param game 게임
     * @return 기록
     */
    @Mapping(target = "stage", source = "currentStage")
    @Mapping(target = "node", expression = "java(game.getCurrentNodeId() == null ? 0 : game.getCurrentNodeId())")
    @Mapping(target = "result", expression = "java(Boolean.TRUE.equals(game.getIsActive()) ? \"PLAYING\" : game.getResult())")
    @Mapping(target = "startedAt", source = "createdAt")
    @Mapping(target = "endedAt", expression = "java(Boolean.TRUE.equals(game.getIsActive()) ? null : game.getUpdatedAt())")
    GameHistoryDTO toHistoryDTO(GameStateEntity game);

    /**
     * @param wraith 망령
     * @return 화면용 망령
     */
    @Mapping(target = "skills", expression = "java(wraith.skillNames())")
    @Mapping(target = "escapesLeft", expression = "java(wraith.escapesLeft())")
    WraithDTO toWraithDTO(WraithEntity wraith);

    /**
     * 원정 통계. 통계가 아직 없으면 0으로 채운 값을 받는다 (호출하는 쪽이 {@code new RunStats()}를 넘긴다).
     *
     * @param stats 통계
     * @return 화면용 통계
     */
    RunStatsDTO toRunStatsDTO(RunStats stats);

    /**
     * 걸린 계약. 조항은 힌트만 보낸다. 파기 가능 여부와 비용은 게임이 계산해 넘긴다.
     *
     * @param contract  계약
     * @param breakable 지금 파기할 수 있으면 true
     * @param breakCost 파기 비용
     * @return 화면용 계약
     */
    @Mapping(target = "boon", expression = "java(contract.boon().title())")
    @Mapping(target = "hint", expression = "java(contract.clause().hint())")
    ContractDTO toContractDTO(DemonContract contract, boolean breakable, int breakCost);

    /**
     * 서약 하나. 점수 보너스는 백분율 정수로 보낸다.
     *
     * @param pact 서약
     * @return 화면용 서약
     */
    default PactDTO toPactDTO(Pact pact) {
        return new PactDTO(pact.name(), pact.title(), pact.description(), (int) Math.round(pact.scoreBonus() * 100));
    }

    /**
     * 성향과 가호. 세력별 단계표(말풍선용)를 함께 싣는다.
     *
     * @param alignment 성향
     * @return 화면용 성향
     */
    default AlignmentDTO toAlignmentDTO(Alignment alignment) {
        Faction dominant = alignment.dominant();
        return new AlignmentDTO(alignment.heaven(), alignment.abyss(), alignment.human(),
                dominant == null ? null : dominant.name(), alignment.blessing(),
                alignment.tier(), alignment.nextTierAt(), factionTable());
    }

    /** @return 세 세력의 경향, 단계별 가호, 헌신 보상 (규칙 상수에서 만든다) */
    private static List<AlignmentDTO.FactionInfo> factionTable() {
        return Arrays.stream(Faction.values())
                .map(faction -> new AlignmentDTO.FactionInfo(faction.name(), faction.label(), faction.tendency(),
                        IntStream.rangeClosed(1, Alignment.MAX_TIER)
                                .mapToObj(tier -> new AlignmentDTO.TierInfo(tier, Alignment.TIER_THRESHOLDS[tier - 1],
                                        Alignment.blessingText(faction, tier)))
                                .toList(),
                        "성향 %d를 넘긴 뒤에는 고를 때마다 %s".formatted(Alignment.TIER_THRESHOLDS[Alignment.MAX_TIER - 1],
                                Alignment.devotionText(faction))))
                .toList();
    }
}
