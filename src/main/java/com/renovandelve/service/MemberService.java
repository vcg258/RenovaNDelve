package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.request.ContactChangeRequest;
import com.renovandelve.dto.request.NicknameChangeRequest;
import com.renovandelve.dto.request.PasswordChangeRequest;
import com.renovandelve.dto.request.SignupRequest;
import com.renovandelve.dto.response.IllustratedEncyclopediaDTO;
import com.renovandelve.dto.response.MemberProfileDTO;

public interface MemberService {

    /** 회원가입 (아이디/닉네임 중복 검사 + BCrypt 해시) */
    void signup(SignupRequest request);

    /** 아이디 사용 가능 여부 */
    boolean isLoginIdAvailable(String loginId);

    /** 닉네임 사용 가능 여부 */
    boolean isNicknameAvailable(String nickname);

    /** 내 프로필 (도감 수집 수, 최고 점수 포함) */
    MemberProfileDTO getProfile(MemberEntity member);

    /** 비밀번호 변경 (현재 비밀번호 확인) */
    void changePassword(MemberEntity member, PasswordChangeRequest request);

    /** 닉네임 변경 */
    void changeNickname(MemberEntity member, NicknameChangeRequest request);

    /** 이름·이메일 등록 / 변경 (비밀번호가 있는 계정은 현재 비밀번호 확인) */
    void changeContact(MemberEntity member, ContactChangeRequest request);

    /** 회원 탈퇴 (소프트 삭제 - 랭킹/도감 기록은 보존) */
    void withdraw(MemberEntity member);

    /** 회원 도감 전체 조회 */
    IllustratedEncyclopediaDTO getEncyclopedia(MemberEntity member);
}
