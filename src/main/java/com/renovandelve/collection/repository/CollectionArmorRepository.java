package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionArmorEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.armorentity.ArmorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionArmorRepository extends JpaRepository<CollectionArmorEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndArmor(MemberEntity member, ArmorEntity armor);
}
