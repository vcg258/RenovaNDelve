package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.NoSuchElementException;

/**
 * SecurityContext(JWT 클레임의 loginId)로부터 현재 회원 엔티티를 해석한다.
 * 컨트롤러가 memberId를 파라미터로 받지 않게 하여 타인 세션 조작을 차단.
 */
@Service
@RequiredArgsConstructor
public class CurrentMemberService {
    private final MemberRepository memberRepository;

    public MemberEntity getCurrentMember() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            throw new NoSuchElementException("인증 정보가 없습니다.");
        }
        return memberRepository.findByLoginId(authentication.getName())
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다: " + authentication.getName()));
    }
}
