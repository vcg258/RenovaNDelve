package com.renovandelve.repository;

import com.renovandelve.domain.CollectionConsumableEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.consumableentity.ConsumableEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionConsumableRepository extends JpaRepository<CollectionConsumableEntity, Long> {
    boolean existsByMemberAndConsumable(MemberEntity member, ConsumableEntity consumable);
}
