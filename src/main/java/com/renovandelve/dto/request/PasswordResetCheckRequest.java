package com.renovandelve.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 재설정 링크 유효 여부 확인. 토큰이 주소(쿼리)나 접속 로그에 남지 않도록 본문으로 받는다.
 */
public record PasswordResetCheckRequest(

        @NotBlank(message = "재설정 링크가 올바르지 않습니다.")
        @Size(max = 128, message = "재설정 링크가 올바르지 않습니다.")
        String token
) {
}
