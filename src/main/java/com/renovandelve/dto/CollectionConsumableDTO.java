package com.renovandelve.dto;

import java.time.LocalDateTime;

public record CollectionConsumableDTO (
    Long collectionId, // 도감 항목 고유 ID
    LocalDateTime acquiredAt, // 최초 흭득 일시
    MemberDTO member, // 회원 ID (FK)
    ConsumableDTO consumable // 소모품 고유 ID (FK)
) {}
