package com.renovandelve.member.repository;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.domain.memberentity.MemberStatus;
import com.renovandelve.member.domain.memberentity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Long>,
        JpaSpecificationExecutor<MemberEntity> {
    // 회원인지 구분하기 위함
    Optional<MemberEntity> findByLoginId(String loginId);

    // 소셜로그인 회원인지 구분
    Optional<MemberEntity> findBySocialId(String socialId);

    // 이메일로 회원 찾기 (아이디 찾기 / 비밀번호 재설정). 이메일은 소문자로 정규화해 저장한다
    Optional<MemberEntity> findByEmail(String email);

    // 이메일 중복 확인
    boolean existsByEmail(String email);

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);

    // 관리자: 상태별 집계
    long countByStatus(MemberStatus status);

    long countByRole(Role role);

    long countByCreatedAtAfter(LocalDateTime dateTime);
}
