package com.renovandelve.repository;

import com.renovandelve.domain.CollectionHelmetEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.helmetentity.HelmetEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionHelmetRepository extends JpaRepository<CollectionHelmetEntity, Long> {
    boolean existsByMemberAndHelmet(MemberEntity member, HelmetEntity helmet);
}
