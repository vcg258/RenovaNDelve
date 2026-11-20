package com.renovandelve.game.repository;

import com.renovandelve.game.domain.GameSkillSlotEntity;
import org.springframework.data.jpa.repository.JpaRepository;


public interface GameSkillSlotRepository extends JpaRepository<GameSkillSlotEntity, Long> {
    // 현재 장착 중인 스킬 조회

    // 스킬 하나 조회
}
