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
        String name,    // 등록하지 않았으면 null (이름·이메일 수집 이전에 가입한 계정)
        String email,   // 등록하지 않았으면 null -> 아이디 찾기 / 비밀번호 찾기를 쓸 수 없다
        boolean hasPassword, // 소셜 로그인 계정은 false (이메일 변경 시 비밀번호 확인을 생략한다)
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
                member.getName(), member.getEmail(), member.getPasswordHash() != null,
                member.getRole().name(), member.getStatus().name(),
                member.getLastLoginAt(), member.getCreatedAt(), collectionCount, bestScore);
    }
}
