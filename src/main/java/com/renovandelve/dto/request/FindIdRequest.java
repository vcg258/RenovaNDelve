package com.renovandelve.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 아이디 찾기 요청. 이름과 이메일이 모두 맞아야 메일이 나간다.
 */
public record FindIdRequest(

        @NotBlank(message = "이름을 입력해주세요.")
        @Size(min = 2, max = 30, message = "이름은 2~30자여야 합니다.")
        @Pattern(regexp = "^[가-힣a-zA-Z]+( [가-힣a-zA-Z]+)*$", message = "이름은 한글과 영문만 사용할 수 있습니다.")
        String name,

        @NotBlank(message = "이메일을 입력해주세요.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        @Size(max = 254, message = "이메일이 너무 깁니다.")
        String email
) {
}
