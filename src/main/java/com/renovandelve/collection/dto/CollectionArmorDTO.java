package com.renovandelve.collection.dto;

import com.renovandelve.catalog.dto.ArmorDTO;
import com.renovandelve.member.dto.MemberDTO;

import java.time.LocalDateTime;

public record CollectionArmorDTO (
    Long collectionId, // 도감 항목 고유 ID
    LocalDateTime acquiredAt, // 최초 흭득 일시
    MemberDTO member, // 회원 ID (FK)
    ArmorDTO armor // 갑옷 고유 ID (FK)
) {}
