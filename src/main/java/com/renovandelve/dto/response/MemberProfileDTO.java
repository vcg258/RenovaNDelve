package com.renovandelve.dto.response;

import com.renovandelve.domain.MemberEntity;

import java.time.LocalDateTime;

/**
 * 내 정보 조회 응답.
 */
public record MemberProfileDTO(
        Long memberId,
        String loginId,
        String nickname,
        String role,
        String status,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        int collectionCount,
        int bestScore
) {
    public static MemberProfileDTO of(MemberEntity member, int collectionCount, int bestScore) {
        return new MemberProfileDTO(
                member.getMemberId(), member.getLoginId(), member.displayName(),
                member.getRole().name(), member.getStatus().name(),
                member.getLastLoginAt(), member.getCreatedAt(), collectionCount, bestScore);
    }
}
