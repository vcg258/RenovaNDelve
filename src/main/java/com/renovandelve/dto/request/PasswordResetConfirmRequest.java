package com.renovandelve.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 비밀번호 재설정 확정. 비밀번호 규칙은 가입·변경과 같다.
 */
public record PasswordResetConfirmRequest(

        @NotBlank(message = "재설정 링크가 올바르지 않습니다.")
        @Size(max = 128, message = "재설정 링크가 올바르지 않습니다.")
        String token,

        @NotBlank(message = "새 비밀번호를 입력해주세요.")
        @Size(min = 8, max = 64, message = "비밀번호는 8자 이상이어야 합니다.")
        @Pattern(regexp = "^(?=.*[a-zA-Z])(?=.*\\d).+$", message = "비밀번호는 영문과 숫자를 모두 포함해야 합니다.")
        String newPassword
) {
}
