package com.renovandelve.service.security;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

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

        return User.builder()
                .username(member.getLoginId())
                .password(member.getPasswordHash())
                .build();
    }
}
