package com.renovandelve.catalog.dto;

import com.renovandelve.catalog.domain.consumableentity.Type;

public record ConsumableDTO (
    Long consumableId, // 소모품 고유 ID
    String name, // 소모품 명
    Type effectType, // 회복 / 공격력 / 방어력 / 해제 / 부여 / 데미지 / 부활
    int effectValue, // 효과 수치
    int scoreCost, // 스타팅 포인트 비용
    String description // 소모품 설명
) {}
