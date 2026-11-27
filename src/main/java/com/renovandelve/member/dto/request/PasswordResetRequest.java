package com.renovandelve.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 재설정 링크 요청. 아이디와 이메일이 모두 맞아야 메일이 나간다.
 */
public record PasswordResetRequest(

        @NotBlank(message = "아이디를 입력해주세요.")
        @Size(max = 50, message = "아이디가 너무 깁니다.")
        String loginId,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일이 너무 깁니다.")
        String email
) {
}
