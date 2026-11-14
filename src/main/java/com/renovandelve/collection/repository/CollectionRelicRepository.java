package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionRelicEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRelicRepository extends JpaRepository<CollectionRelicEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndRelic(MemberEntity member, RelicEntity relic);
}
