package com.renovandelve.dto.request;

import java.util.List;

/**
 * 전투 중 스킬 사용 요청.
 * slotId = 플레이어의 스킬 슬롯 ID, targetKeys = 전투 세션 내 몬스터 key 목록
 */
public record SkillUseRequest(
        Long slotId,
        List<Integer> targetKeys
) {
}
