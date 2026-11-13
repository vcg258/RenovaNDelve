package com.renovandelve.repository;

import com.renovandelve.domain.CollectionHelmetEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionHelmetRepository extends JpaRepository<CollectionHelmetEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndHelmet(MemberEntity member, HelmetEntity helmet);
}
