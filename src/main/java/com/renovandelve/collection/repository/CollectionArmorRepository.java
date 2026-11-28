package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionArmorEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface CollectionArmorRepository extends JpaRepository<CollectionArmorEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndArmor(MemberEntity member, ArmorEntity armor);

    /** 도감에 등록한 갑옷 ID (원정 준비 해금 판정) */
    @Query("select c.armor.armorId from CollectionArmorEntity c where c.member = :member")
    Set<Long> findArmorIdsByMember(@Param("member") MemberEntity member);
}
