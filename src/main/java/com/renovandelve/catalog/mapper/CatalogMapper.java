package com.renovandelve.catalog.mapper;

import com.renovandelve.catalog.domain.MonsterEntity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.catalog.dto.ConsumableDTO;
import com.renovandelve.catalog.dto.HelmetDTO;
import com.renovandelve.catalog.dto.MonsterDTO;
import com.renovandelve.catalog.dto.RelicDTO;
import com.renovandelve.catalog.dto.SkillDTO;
import com.renovandelve.catalog.dto.WeaponDTO;
import com.renovandelve.game.dto.SkillInfoDTO;
import com.renovandelve.global.config.MapStructConfig;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * 게임 데이터(장비·스킬·유물·소모품·몬스터) 엔티티를 DTO로 옮긴다. 필드 이름이 같아 설정 없이 1:1로 옮겨진다.
 */
@Mapper(config = MapStructConfig.class)
public interface CatalogMapper {

    /**
     * @param weapon 무기
     * @return DTO (무기가 null이면 null)
     */
    WeaponDTO toWeaponDTO(WeaponEntity weapon);

    /**
     * @param weapons 무기 목록
     * @return DTO 목록
     */
    List<WeaponDTO> toWeaponDTOs(List<WeaponEntity> weapons);

    /**
     * @param armor 갑옷
     * @return DTO (갑옷이 null이면 null)
     */
    ArmorDTO toArmorDTO(ArmorEntity armor);

    /**
     * @param armors 갑옷 목록
     * @return DTO 목록
     */
    List<ArmorDTO> toArmorDTOs(List<ArmorEntity> armors);

    /**
     * @param helmet 투구
     * @return DTO (투구가 null이면 null)
     */
    HelmetDTO toHelmetDTO(HelmetEntity helmet);

    /**
     * @param helmets 투구 목록
     * @return DTO 목록
     */
    List<HelmetDTO> toHelmetDTOs(List<HelmetEntity> helmets);

    /**
     * 스킬을 슬롯·도감용 DTO로 옮긴다 (ID 없음).
     *
     * @param skill 스킬
     * @return DTO
     */
    SkillDTO toSkillDTO(SkillEntity skill);

    /**
     * 스킬을 고르는 화면(원정 준비, 보상)용 DTO로 옮긴다 (ID 포함).
     *
     * @param skill 스킬
     * @return DTO
     */
    SkillInfoDTO toSkillInfoDTO(SkillEntity skill);

    /**
     * @param skills 스킬 목록
     * @return 고르는 화면용 DTO 목록
     */
    List<SkillInfoDTO> toSkillInfoDTOs(List<SkillEntity> skills);

    /**
     * @param relic 유물
     * @return DTO
     */
    RelicDTO toRelicDTO(RelicEntity relic);

    /**
     * @param relics 유물 목록
     * @return DTO 목록
     */
    List<RelicDTO> toRelicDTOs(List<RelicEntity> relics);

    /**
     * @param consumable 소모품
     * @return DTO
     */
    ConsumableDTO toConsumableDTO(ConsumableEntity consumable);

    /**
     * @param consumables 소모품 목록
     * @return DTO 목록
     */
    List<ConsumableDTO> toConsumableDTOs(List<ConsumableEntity> consumables);

    /**
     * @param monster 몬스터
     * @return DTO
     */
    MonsterDTO toMonsterDTO(MonsterEntity monster);

    /**
     * @param monsters 몬스터 목록
     * @return DTO 목록
     */
    List<MonsterDTO> toMonsterDTOs(List<MonsterEntity> monsters);
}
