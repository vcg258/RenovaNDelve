package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionRelicEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.relicentity.RelicEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface CollectionRelicRepository extends JpaRepository<CollectionRelicEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndRelic(MemberEntity member, RelicEntity relic);

    /** 도감에 등록한 유물 ID (원정 준비 해금 판정) */
    @Query("select c.relic.relicId from CollectionRelicEntity c where c.member = :member")
    Set<Long> findRelicIdsByMember(@Param("member") MemberEntity member);
}
