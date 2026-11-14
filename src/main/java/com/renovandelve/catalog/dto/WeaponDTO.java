package com.renovandelve.catalog.dto;

import com.renovandelve.catalog.domain.weaponentity.Type;

public record WeaponDTO (
    Long weaponId, // 무기 고유 ID (PK)
    String name, // 무기명
    Type type, // 검 / 단검 / 둔기 / 스태프
    int atkBonus, // 추가 공격력 (단검 : 데미지 - , 둔기 : 데미지 +)
    short specialEffect, // 무기 특수 효과
    int scoreCost, // 스타딩 포인트 비용
//    int price,      // 가격
    String description // 무기 설명
) {}
