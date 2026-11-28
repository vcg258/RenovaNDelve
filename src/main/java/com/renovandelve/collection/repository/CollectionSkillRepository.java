package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Set;

public interface CollectionSkillRepository extends JpaRepository<CollectionSkillEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndSkill(MemberEntity member, SkillEntity skill);

    /** 도감에 등록한 스킬 ID (원정 준비 해금 판정) */
    @Query("select c.skill.skillId from CollectionSkillEntity c where c.member = :member")
    Set<Long> findSkillIdsByMember(@Param("member") MemberEntity member);
}
