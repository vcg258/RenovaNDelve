package com.renovandelve.catalog.dto;

import com.renovandelve.catalog.domain.helmetentity.Type;

public record HelmetDTO (
    Long helmetId, // 투구 고유 ID
    String name, // 투구 명
    Type type, // 가죽모자 / 천 / 판금 / 가면
    int hpBonus, // 추가 체력
    short specialEffect, // 투구 특수 효과 설명
    int scoreCost, // 스타팅 포인트 비용
//    int price,      // 가격
    String description // 투구 설명
) {}
