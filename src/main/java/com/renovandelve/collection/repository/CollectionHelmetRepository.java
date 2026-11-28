package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionHelmetEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.helmetentity.HelmetEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface CollectionHelmetRepository extends JpaRepository<CollectionHelmetEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndHelmet(MemberEntity member, HelmetEntity helmet);

    /** 도감에 등록한 투구 ID (원정 준비 해금 판정) */
    @Query("select c.helmet.helmetId from CollectionHelmetEntity c where c.member = :member")
    Set<Long> findHelmetIdsByMember(@Param("member") MemberEntity member);
}
