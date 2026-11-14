package com.renovandelve.auth.service;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.repository.MemberRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class Oauth2UserService extends DefaultOAuth2UserService {
    private final MemberRepository memberRepository;

    /**
     * 구글 사용자 정보를 받아 회원을 찾거나 만든다. 개인정보 최소 수집을 위해 제공자 고유 ID(sub)만 쓴다.
     *
     * @param userRequest OAuth2 요청
     * @return OAuth2 사용자
     */
    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String socialId = (String) attributes.get("sub");
        // 개인정보 최소 수집: 서비스가 쓰는 것은 제공자 고유 ID(sub)뿐이다.
        // - 이메일은 어디에도 쓰지 않으므로 읽지도, 로그에 남기지도 않는다.
        // - 구글 AccessToken은 로그인 이후 구글 API를 호출하지 않으므로 DB에 저장하지 않는다.
        //   (평문 저장된 제3자 토큰은 DB 유출 시 그대로 2차 피해가 된다)
        log.info("OAuth2 로그인 시도");

        if (memberRepository.findBySocialId(socialId).isEmpty()) {
            memberRepository.save(MemberEntity.builder().socialId(socialId).build());
        }

        return oAuth2User;
    }
}
