package com.renovandelve.domain.memberentity;

/**
 * 계정 상태. 로그인 가능 여부를 결정한다.
 */
public enum MemberStatus {
    ACTIVE,     // 정상
    SUSPENDED,  // 운영자에 의한 이용 정지
    WITHDRAWN;  // 탈퇴 (소프트 삭제 - 랭킹/도감 기록 보존)

    public boolean canLogin() {
        return this == ACTIVE;
    }
}
