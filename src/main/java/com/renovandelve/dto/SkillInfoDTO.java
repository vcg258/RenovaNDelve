package com.renovandelve.dto;

import com.renovandelve.domain.skillentity.SkillEntity;
import com.renovandelve.domain.skillentity.Target;

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
    public static SkillInfoDTO from(SkillEntity s) {
        return new SkillInfoDTO(s.getSkillId(), s.getName(), s.getTarget(), s.getAttackCount(),
                s.getCoefficient(), s.getCost(), s.getCooldown(), s.getStatusEffect(),
                s.getEffectStack(), s.getDescription());
    }
}
