package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.request.SignupRequest;
import com.renovandelve.dto.response.IllustratedEncyclopediaDTO;

public interface MemberService {

    /**
     * 회원가입 (아이디 중복 검사 + BCrypt 해시)
     */
    void signup(SignupRequest request);

    /**
     * 회원 도감 전체 조회
     */
    IllustratedEncyclopediaDTO getEncyclopedia(MemberEntity member);
}
