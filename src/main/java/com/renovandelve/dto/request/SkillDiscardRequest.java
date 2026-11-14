package com.renovandelve.dto.request;

import jakarta.validation.constraints.NotNull;

/**
 * 스킬 버리기. slotId = 버릴 스킬이 들어 있는 슬롯
 */
public record SkillDiscardRequest(
        @NotNull(message = "버릴 스킬을 선택해주세요.")
        Long slotId
) {
}
