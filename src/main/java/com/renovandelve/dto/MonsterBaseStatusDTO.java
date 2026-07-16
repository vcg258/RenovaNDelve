package com.renovandelve.dto;

public record MonsterBaseStatusDTO (
    Long statusId, // 스텟 고유 ID
    short stage, // 등장 스테이지 정보
    short difficulty, // 난이도 설정
    int baseHp, // 기본 체력
    int baseAtk, // 기본 공격력
    int baseDef // 기본 방어력
) {}