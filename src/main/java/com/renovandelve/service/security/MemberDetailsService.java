package com.renovandelve.service.security;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Log4j2
@Service
@RequiredArgsConstructor
public class MemberDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    @Override
    public UserDetails loadUserByUsername(String loginId) throws UsernameNotFoundException {
        MemberEntity member = memberRepository.findByLoginId(loginId).orElseThrow(
                () -> new UsernameNotFoundException("로그인 시도한 사용자 없음")
        );

        // 계정 상태를 UserDetails 플래그로 넘겨 Spring Security가 직접 차단하게 한다
        // (정지 계정 -> DisabledException, 탈퇴 계정 -> 로그인 불가)
        boolean enabled = member.getStatus() != null && member.getStatus().canLogin();

        return User.builder()
                .username(member.getLoginId())
                .password(member.getPasswordHash() == null ? "{noop}__WITHDRAWN__" : member.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(member.getRole().authority())))
                .disabled(!enabled)
                .build();
    }
}
