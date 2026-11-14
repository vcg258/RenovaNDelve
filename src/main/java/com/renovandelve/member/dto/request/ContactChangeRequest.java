package com.renovandelve.member.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 이름·이메일 등록 / 변경 (내 정보).
 * 이메일은 비밀번호 재설정 링크가 가는 곳이므로, 비밀번호가 있는 계정은 현재 비밀번호를 다시 확인한다.
 * (로그인된 화면을 잠깐 빌린 사람이 복구 수단을 자기 것으로 바꾸는 것 방지)
 */
public record ContactChangeRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(min = 2, max = 30, message = "이름은 2~30자여야 합니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z]+( [가-힣a-zA-Z]+)*$", message = "이름은 한글과 영문만 사용할 수 있습니다.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일이 너무 깁니다.")
        String email,

        @Size(max = 64, message = "비밀번호가 올바르지 않습니다.")
        String currentPassword
) {
}
