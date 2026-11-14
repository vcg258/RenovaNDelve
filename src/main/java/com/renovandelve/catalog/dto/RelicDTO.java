package com.renovandelve.catalog.dto;

import com.renovandelve.catalog.domain.relicentity.Rarity;

public record RelicDTO (
    Long relicId, // 유물 고유 ID
    String name, // 유물 명
//    int price,      // 가격
    String effectDesc, // 유물 효과 설명
    Rarity rarity, // 희귀도 (COMMON / RARE / EPIC)
    int scoreCost // 스타팅 포인트 비용
) {}
