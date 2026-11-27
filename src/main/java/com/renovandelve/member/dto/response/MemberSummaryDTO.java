package com.renovandelve.member.dto.response;

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
}
