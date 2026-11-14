package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionConsumableEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.consumableentity.ConsumableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionConsumableRepository extends JpaRepository<CollectionConsumableEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndConsumable(MemberEntity member, ConsumableEntity consumable);
}
