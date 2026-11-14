package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionHelmetEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionHelmetRepository extends JpaRepository<CollectionHelmetEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndHelmet(MemberEntity member, HelmetEntity helmet);
}
