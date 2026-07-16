package com.renovandelve.dto;

import java.time.LocalDateTime;

public record CollectionHelmetDTO (
    Long collectionId, // 도감 항목 고유 ID
    LocalDateTime acquiredAt, // 최초 흭득 일시
    MemberDTO member, // 회원 ID (FK)
    HelmetDTO helmet // 투구 고유 ID (FK)
) {}
