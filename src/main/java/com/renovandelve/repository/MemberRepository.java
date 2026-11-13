package com.renovandelve.repository;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.memberentity.MemberStatus;
import com.renovandelve.domain.memberentity.Role;
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

    // 닉네임 중복 확인
    boolean existsByNickname(String nickname);

    // 관리자: 상태별 집계
    long countByStatus(MemberStatus status);

    long countByRole(Role role);

    long countByCreatedAtAfter(LocalDateTime dateTime);
}
