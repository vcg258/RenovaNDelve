package com.renovandelve.repository;

import com.renovandelve.domain.CollectionRelicEntity;
import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.relicentity.RelicEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CollectionRelicRepository extends JpaRepository<CollectionRelicEntity, Long> {
    boolean existsByMemberAndRelic(MemberEntity member, RelicEntity relic);
}
