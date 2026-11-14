package com.renovandelve.auth.service;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberDetailsService implements UserDetailsService {
    private final MemberRepository memberRepository;

    /**
     * 로그인 아이디로 회원을 찾아 스프링 시큐리티용 사용자 정보로 만든다.
     * 계정 상태(정지·탈퇴)를 플래그로 넘겨 시큐리티가 직접 막게 한다.
     *
     * @param loginId 로그인 아이디
     * @return 사용자 정보
     * @throws UsernameNotFoundException 없는 아이디면
     */
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
