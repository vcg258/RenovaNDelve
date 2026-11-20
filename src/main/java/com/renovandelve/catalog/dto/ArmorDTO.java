package com.renovandelve.catalog.dto;

import com.renovandelve.catalog.domain.Rarity;
import com.renovandelve.catalog.domain.armorentity.Type;

public record ArmorDTO (
    Long armorId, // 갑옷 고유 ID
    String name, // 갑옷 명
    Type type, // 가죽 / 천 / 체인 / 판금
    int defBonus, // 추가 방어력 (단검 -, 둔기 +)
    short specialEffect, // 갑옷 특수 효과
    int scoreCost, // 스타팅 포인트 비용
    String description, // 갑옷 설명
    Rarity rarity // 등급 (COMMON / RARE / EPIC)
) {}