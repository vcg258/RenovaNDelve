package com.renovandelve.dto.request;

public record SignupRequest(
        String loginId,  // 로그인 아이디
        String password  // 비밀번호 (평문 -> 서버에서 BCrypt 해시)
) {
}
