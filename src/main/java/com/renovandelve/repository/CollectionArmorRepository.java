package com.renovandelve.repository;

import com.renovandelve.domain.CollectionArmorEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.armorentity.ArmorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionArmorRepository extends JpaRepository<CollectionArmorEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndArmor(MemberEntity member, ArmorEntity armor);
}
