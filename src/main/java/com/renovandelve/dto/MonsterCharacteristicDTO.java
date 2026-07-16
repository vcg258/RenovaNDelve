package com.renovandelve.dto;

public record MonsterCharacteristicDTO (
    Long keywordId, // 키워드 고유 ID (PK)
    String keyword, // 키워드 명
    MonsterDTO monster // 몬스터 ID (FK)
) {}
