package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionWeaponEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.weaponentity.WeaponEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface CollectionWeaponRepository extends JpaRepository<CollectionWeaponEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndWeapon(MemberEntity member, WeaponEntity weapon);

    /** 도감에 등록한 무기 ID (원정 준비 해금 판정) */
    @Query("select c.weapon.weaponId from CollectionWeaponEntity c where c.member = :member")
    Set<Long> findWeaponIdsByMember(@Param("member") MemberEntity member);
}
