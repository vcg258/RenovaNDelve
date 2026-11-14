package com.renovandelve.collection.repository;

import com.renovandelve.collection.domain.CollectionSkillEntity;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.catalog.domain.skillentity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionSkillRepository extends JpaRepository<CollectionSkillEntity, Long> {
    // 도감 수집 수 (스타팅 포인트 보너스 계산)
    long countByMember(MemberEntity member);

    boolean existsByMemberAndSkill(MemberEntity member, SkillEntity skill);
}
