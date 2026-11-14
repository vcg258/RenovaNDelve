package com.renovandelve.member.dto.response;

import com.renovandelve.member.domain.MemberEntity;

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
    /**
     * 회원을 관리자 목록 한 줄로 옮긴다.
     *
     * @param member  회원
     * @param playing 진행 중인 게임이 있는지
     * @return 회원 요약
     */
    public static MemberSummaryDTO of(MemberEntity member, boolean playing) {
        return new MemberSummaryDTO(
                member.getMemberId(), member.getLoginId(), member.displayName(),
                member.getRole().name(), member.getStatus().name(), member.getStatusReason(),
                member.getLastLoginAt(), member.getCreatedAt(), playing);
    }
}
