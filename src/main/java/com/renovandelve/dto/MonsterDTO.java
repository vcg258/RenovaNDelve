package com.renovandelve.dto;

public record MonsterDTO (
    Long monsterId, // 몬스터 고유 ID
    String name, // 몬스터 명
    short monsterType, // 몬스터 타입 NORMAL 0 / ELITE 1 / BOSS 2
    int scoreReward, // 처치 시 기본 점수
    String description // 몬스터 설명
) {}
