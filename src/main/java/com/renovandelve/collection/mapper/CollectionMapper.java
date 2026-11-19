package com.renovandelve.collection.mapper;

import com.renovandelve.catalog.mapper.CatalogMapper;
import com.renovandelve.collection.domain.CollectionArmorEntity;
import com.renovandelve.collection.domain.CollectionConsumableEntity;
import com.renovandelve.collection.domain.CollectionHelmetEntity;
import com.renovandelve.collection.domain.CollectionRelicEntity;
import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.collection.domain.CollectionWeaponEntity;
import com.renovandelve.collection.dto.CollectionArmorDTO;
import com.renovandelve.collection.dto.CollectionConsumableDTO;
import com.renovandelve.collection.dto.CollectionHelmetDTO;
import com.renovandelve.collection.dto.CollectionRelicDTO;
import com.renovandelve.collection.dto.CollectionSkillDTO;
import com.renovandelve.collection.dto.CollectionWeaponDTO;
import com.renovandelve.global.config.MapStructConfig;
import com.renovandelve.member.mapper.MemberMapper;
import org.mapstruct.Mapper;

/**
 * 도감 항목 엔티티를 DTO로 옮긴다. 회원은 ID와 표시 이름만({@link MemberMapper#toPublicDTO}), 항목 자체는 {@link CatalogMapper}가 옮긴다.
 * 게임이 끝날 때 새로 등록한 항목(도감 등록)과 내 도감 조회가 같은 변환을 쓴다.
 */
@Mapper(config = MapStructConfig.class, uses = {CatalogMapper.class, MemberMapper.class})
public interface CollectionMapper {

    /**
     * @param entry 스킬 도감 항목
     * @return DTO
     */
    CollectionSkillDTO toDTO(CollectionSkillEntity entry);

    /**
     * @param entry 무기 도감 항목
     * @return DTO
     */
    CollectionWeaponDTO toDTO(CollectionWeaponEntity entry);

    /**
     * @param entry 갑옷 도감 항목
     * @return DTO
     */
    CollectionArmorDTO toDTO(CollectionArmorEntity entry);

    /**
     * @param entry 투구 도감 항목
     * @return DTO
     */
    CollectionHelmetDTO toDTO(CollectionHelmetEntity entry);

    /**
     * @param entry 유물 도감 항목
     * @return DTO
     */
    CollectionRelicDTO toDTO(CollectionRelicEntity entry);

    /**
     * @param entry 소모품 도감 항목
     * @return DTO
     */
    CollectionConsumableDTO toDTO(CollectionConsumableEntity entry);
}
