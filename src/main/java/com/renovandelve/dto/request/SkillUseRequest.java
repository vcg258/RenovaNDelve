package com.renovandelve.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 전투 중 스킬 사용 요청.
 * slotId = 플레이어의 스킬 슬롯 ID, targetKeys = 전투 세션 내 몬스터 key 목록
 */
public record SkillUseRequest(
        @NotNull(message = "사용할 스킬을 선택해주세요.")
        Long slotId,

        @Size(max = 10, message = "대상 지정이 올바르지 않습니다.")
        List<Integer> targetKeys
) {
}
