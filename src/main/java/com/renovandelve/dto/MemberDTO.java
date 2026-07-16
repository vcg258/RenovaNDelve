package com.renovandelve.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.*;

import java.time.LocalDateTime;

@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class MemberDTO {
    private Long memberId; // 회원 고유 ID
    private String loginId; // 회원 로그인 아이디
    @JsonIgnore
    private String passwordHash; // 비밀번호 해시값 (Bcrypt)
    private String socialToken; // 소셜 OAuth2 토큰
    private String socialId; // 소셜 제공자 내 고유 ID
    private LocalDateTime createdAt; // 계정 생성일시
    private LocalDateTime updatedAt; // 마지막 수정일시
}