package com.renovandelve.repository;

import com.renovandelve.domain.CollectionSkillEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.skillentity.SkillEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionSkillRepository extends JpaRepository<CollectionSkillEntity, Long> {
    boolean existsByMemberAndSkill(MemberEntity member, SkillEntity skill);
}
