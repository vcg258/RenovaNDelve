package com.renovandelve.dto;

import com.renovandelve.domain.consumableentity.Type;

public record MonsterBuffDTO (
        Long keywordId, // 키워드 고유 ID
        String keyword, // 키워드 명
        Type buffType,  // 버프 타입
        String effectDesc // 키워드 효과 설명
) {}
