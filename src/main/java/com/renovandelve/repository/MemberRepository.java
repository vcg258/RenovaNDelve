package com.renovandelve.repository;

import com.renovandelve.domain.MemberEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MemberRepository extends JpaRepository<MemberEntity, Long> {
    // 회원인지 구분하기 위함
    Optional<MemberEntity> findByLoginId(String loginId);

    // 소셜로그인 회원인지 구분
    Optional<MemberEntity> findBySocialId(String socialId);
}
