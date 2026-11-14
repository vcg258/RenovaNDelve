package com.renovandelve.catalog.dto;

import com.renovandelve.catalog.domain.skillentity.Target;
import lombok.Builder;

import java.math.BigDecimal;

@Builder
public record SkillDTO (
    String name, // 기술명 (출혈, 화상 등)
    Target target,  // 대상 (자신, 적 1체, 적 전체)
    int attackCount, // 공격 횟수
    BigDecimal coefficient, // 기본 계수 (공격력 배율)
    int cost, // 기술 사용 비용 (AP 등)
    int cooldown, // 쿨타임 (턴 수)
    short statusEffect, // 부여 상태이상 (출혈/화상/독/기절)
    int effectStack, // 상태이상 스택 수
    String description // 기술 설명
) {}
