package com.renovandelve.dto.response;

import com.renovandelve.domain.MemberEntity;

import java.time.LocalDateTime;

/**
 * 관리자 회원 목록 한 줄.
 */
public record MemberSummaryDTO(
        Long memberId,
        String loginId,
        String nickname,
        String role,
        String status,
        String statusReason,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt,
        boolean playing
) {
    public static MemberSummaryDTO of(MemberEntity member, boolean playing) {
        return new MemberSummaryDTO(
                member.getMemberId(), member.getLoginId(), member.displayName(),
                member.getRole().name(), member.getStatus().name(), member.getStatusReason(),
                member.getLastLoginAt(), member.getCreatedAt(), playing);
    }
}
