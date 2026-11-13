package com.renovandelve.repository;

import com.renovandelve.domain.CollectionWeaponEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.weaponentity.WeaponEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionWeaponRepository extends JpaRepository<CollectionWeaponEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndWeapon(MemberEntity member, WeaponEntity weapon);
}
