package com.renovandelve.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 관리자: 회원 제재 / 제재 해제 요청.
 * status = ACTIVE(해제) / SUSPENDED(정지)
 */
public record MemberStatusChangeRequest(

        @NotBlank(message = "변경할 상태를 지정해주세요.")
        String status,

        @Size(max = 200, message = "사유는 200자 이내로 입력해주세요.")
        String reason
) {
}
