package com.renovandelve.domain.memberentity;

/**
 * 회원 권한.
 * Spring Security 규약에 맞춰 "ROLE_" 접두사는 authority 변환 시점에 붙인다.
 */
public enum Role {
    USER,   // 일반 플레이어
    ADMIN;  // 운영자 (회원 관리, ETL, 통계)

    public String authority() {
        return "ROLE_" + name();
    }
}
