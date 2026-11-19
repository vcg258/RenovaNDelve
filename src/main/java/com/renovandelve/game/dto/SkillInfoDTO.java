package com.renovandelve.game.dto;

import com.renovandelve.catalog.domain.skillentity.Target;

import java.math.BigDecimal;

/**
 * 스킬 ID를 포함한 스킬 정보 (세팅 화면 / 보상 선택에서 사용)
 */
public record SkillInfoDTO(
        Long skillId,
        String name,
        Target target,
        int attackCount,
        BigDecimal coefficient,
        int cost,
        int cooldown,
        short statusEffect,
        int effectStack,
        String description
) {
}
