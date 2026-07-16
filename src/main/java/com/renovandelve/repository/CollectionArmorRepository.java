package com.renovandelve.repository;

import com.renovandelve.domain.CollectionArmorEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.armorentity.ArmorEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionArmorRepository extends JpaRepository<CollectionArmorEntity, Long> {
    boolean existsByMemberAndArmor(MemberEntity member, ArmorEntity armor);
}
