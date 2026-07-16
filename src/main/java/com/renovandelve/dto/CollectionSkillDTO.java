package com.renovandelve.dto;

import java.time.LocalDateTime;

public record CollectionSkillDTO (
    Long collectionId, // 도감 항목 고유 ID
    LocalDateTime acquiredAt, // 최초 흭득 일시
    MemberDTO member, // 회원 ID (FK)
    SkillDTO skill // 기술 ID (FK)
) {}
