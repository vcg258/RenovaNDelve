package com.renovandelve.service.security;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.repository.MemberRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import java.util.Map;

@Log4j2
@Service
@Transactional
@RequiredArgsConstructor
public class Oauth2UserService extends DefaultOAuth2UserService {
    private final MemberRepository memberRepository;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        OAuth2User oAuth2User = super.loadUser(userRequest);

        Map<String, Object> attributes = oAuth2User.getAttributes();
        String socialId = (String) attributes.get("sub");
        String email = (String) attributes.get("email");

        String socialToken = userRequest.getAccessToken().getTokenValue();

        log.info("OAuth2 로그인 시도 - socialId: {}, email: {}", socialId, email);

        memberRepository.findBySocialId(socialId)
                .ifPresentOrElse(memberEntity -> {
                            memberEntity.updateSocialToken(socialToken);
                            memberRepository.save(memberEntity);
                        },
                        () -> memberRepository.save(
                                MemberEntity.builder()
                                        .socialId(socialId)
                                        .socialToken(socialToken)
                                        .build()
                        )
                );

        return oAuth2User;
    }
}
