package com.renovandelve.collection.service;

import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
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
import com.renovandelve.collection.repository.CollectionArmorRepository;
import com.renovandelve.collection.repository.CollectionConsumableRepository;
import com.renovandelve.collection.repository.CollectionHelmetRepository;
import com.renovandelve.collection.repository.CollectionRelicRepository;
import com.renovandelve.collection.repository.CollectionSkillRepository;
import com.renovandelve.collection.repository.CollectionWeaponRepository;
import com.renovandelve.collection.mapper.CollectionMapper;
import com.renovandelve.member.domain.MemberEntity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * 회원 도감 등록. 상점 구매, 스킬 습득, 원정 종료가 모두 이 서비스로 등록한다.
 * 한 항목은 회원당 한 번만 등록된다 (테이블의 (member_id, 항목) 유니크 제약과 같은 규칙).
 */
@Service
@RequiredArgsConstructor
public class CollectionService {

    private final CollectionSkillRepository collectionSkillRepository;
    private final CollectionWeaponRepository collectionWeaponRepository;
    private final CollectionArmorRepository collectionArmorRepository;
    private final CollectionHelmetRepository collectionHelmetRepository;
    private final CollectionRelicRepository collectionRelicRepository;
    private final CollectionConsumableRepository collectionConsumableRepository;
    private final CollectionMapper collectionMapper;

    /**
     * 스킬를 도감에 등록한다. 이미 있으면 건너뛴다.
     *
     * @param member 회원 (null이면 건너뛴다)
     * @param skill 스킬 (null이면 건너뛴다)
     * @return 이번에 새로 등록했으면 그 항목
     */
    @Transactional
    public Optional<CollectionSkillDTO> registerSkill(MemberEntity member, SkillEntity skill) {
        if (member == null || skill == null || collectionSkillRepository.existsByMemberAndSkill(member, skill)) {
            return Optional.empty();
        }
        return Optional.of(collectionMapper.toDTO(collectionSkillRepository.save(CollectionSkillEntity.of(member, skill))));
    }

    /**
     * 무기를 도감에 등록한다. 이미 있으면 건너뛴다.
     *
     * @param member 회원 (null이면 건너뛴다)
     * @param weapon 무기 (null이면 건너뛴다)
     * @return 이번에 새로 등록했으면 그 항목
     */
    @Transactional
    public Optional<CollectionWeaponDTO> registerWeapon(MemberEntity member, WeaponEntity weapon) {
        if (member == null || weapon == null || collectionWeaponRepository.existsByMemberAndWeapon(member, weapon)) {
            return Optional.empty();
        }
        return Optional.of(collectionMapper.toDTO(collectionWeaponRepository.save(CollectionWeaponEntity.of(member, weapon))));
    }

    /**
     * 갑옷를 도감에 등록한다. 이미 있으면 건너뛴다.
     *
     * @param member 회원 (null이면 건너뛴다)
     * @param armor 갑옷 (null이면 건너뛴다)
     * @return 이번에 새로 등록했으면 그 항목
     */
    @Transactional
    public Optional<CollectionArmorDTO> registerArmor(MemberEntity member, ArmorEntity armor) {
        if (member == null || armor == null || collectionArmorRepository.existsByMemberAndArmor(member, armor)) {
            return Optional.empty();
        }
        return Optional.of(collectionMapper.toDTO(collectionArmorRepository.save(CollectionArmorEntity.of(member, armor))));
    }

    /**
     * 투구를 도감에 등록한다. 이미 있으면 건너뛴다.
     *
     * @param member 회원 (null이면 건너뛴다)
     * @param helmet 투구 (null이면 건너뛴다)
     * @return 이번에 새로 등록했으면 그 항목
     */
    @Transactional
    public Optional<CollectionHelmetDTO> registerHelmet(MemberEntity member, HelmetEntity helmet) {
        if (member == null || helmet == null || collectionHelmetRepository.existsByMemberAndHelmet(member, helmet)) {
            return Optional.empty();
        }
        return Optional.of(collectionMapper.toDTO(collectionHelmetRepository.save(CollectionHelmetEntity.of(member, helmet))));
    }

    /**
     * 유물를 도감에 등록한다. 이미 있으면 건너뛴다.
     *
     * @param member 회원 (null이면 건너뛴다)
     * @param relic 유물 (null이면 건너뛴다)
     * @return 이번에 새로 등록했으면 그 항목
     */
    @Transactional
    public Optional<CollectionRelicDTO> registerRelic(MemberEntity member, RelicEntity relic) {
        if (member == null || relic == null || collectionRelicRepository.existsByMemberAndRelic(member, relic)) {
            return Optional.empty();
        }
        return Optional.of(collectionMapper.toDTO(collectionRelicRepository.save(CollectionRelicEntity.of(member, relic))));
    }

    /**
     * 소모품를 도감에 등록한다. 이미 있으면 건너뛴다.
     *
     * @param member 회원 (null이면 건너뛴다)
     * @param consumable 소모품 (null이면 건너뛴다)
     * @return 이번에 새로 등록했으면 그 항목
     */
    @Transactional
    public Optional<CollectionConsumableDTO> registerConsumable(MemberEntity member, ConsumableEntity consumable) {
        if (member == null || consumable == null || collectionConsumableRepository.existsByMemberAndConsumable(member, consumable)) {
            return Optional.empty();
        }
        return Optional.of(collectionMapper.toDTO(collectionConsumableRepository.save(CollectionConsumableEntity.of(member, consumable))));
    }
}
