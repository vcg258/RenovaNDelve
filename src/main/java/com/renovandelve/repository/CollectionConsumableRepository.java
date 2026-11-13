package com.renovandelve.repository;

import com.renovandelve.domain.CollectionConsumableEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionConsumableRepository extends JpaRepository<CollectionConsumableEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndConsumable(MemberEntity member, ConsumableEntity consumable);
}
