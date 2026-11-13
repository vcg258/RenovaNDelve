package com.renovandelve.dto.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 관리자: 회원 권한 변경 요청. role = USER / ADMIN
 */
public record RoleChangeRequest(

        @NotBlank(message = "변경할 권한을 지정해주세요.")
        String role
) {
}
