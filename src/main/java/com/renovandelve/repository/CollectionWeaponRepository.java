package com.renovandelve.repository;

import com.renovandelve.domain.CollectionWeaponEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionWeaponRepository extends JpaRepository<CollectionWeaponEntity, Long> {
    boolean existsByMemberAndWeapon(MemberEntity member, WeaponEntity weapon);
}
