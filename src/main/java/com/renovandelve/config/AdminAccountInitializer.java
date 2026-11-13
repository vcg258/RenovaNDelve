package com.renovandelve.config;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.memberentity.Role;
import com.renovandelve.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * 최초 관리자 계정 부트스트랩.
 *
 * 관리자 계정도 일반 회원과 동일하게 member 테이블에 저장된다.
 * (별도 저장소나 하드코딩 계정이 아니라, role = ADMIN 인 정상 레코드다)
 *
 * 이 클래스는 "관리자가 한 명도 없을 때" 최초 1개만 만들어주는 역할이며,
 * 비밀번호를 환경변수로 주지 않으면 임의의 강한 비밀번호를 생성해 로그에 1회만 출력한다.
 * -> 소스나 설정 파일에 비밀번호가 남지 않는다.
 */
@Log4j2
@Configuration
@RequiredArgsConstructor
public class AdminAccountInitializer {

    @Value("${renovaNDelve.admin.login-id:admin}")
    private String adminLoginId;

    /** 미지정 시 임의 생성 (설정 파일에 기본 비밀번호를 두지 않기 위함) */
    @Value("${renovaNDelve.admin.password:}")
    private String adminPassword;

    @Bean
    public ApplicationRunner initAdminAccount(MemberRepository memberRepository, PasswordEncoder passwordEncoder) {
        return args -> {
            if (memberRepository.countByRole(Role.ADMIN) > 0) {
                return; // 이미 관리자 존재 -> 아무것도 하지 않음
            }

            // 같은 아이디의 일반 회원이 이미 있으면 승격 (비밀번호는 기존 것을 유지)
            var existing = memberRepository.findByLoginId(adminLoginId);
            if (existing.isPresent()) {
                MemberEntity member = existing.get();
                member.changeRole(Role.ADMIN);
                memberRepository.save(member);
                log.warn("""

                        ============================================================
                         기존 계정 '{}' 을(를) 관리자로 승격했습니다.
                         비밀번호는 기존 그대로입니다.
                        ============================================================""", adminLoginId);
                return;
            }

            boolean generated = adminPassword == null || adminPassword.isBlank();
            String rawPassword = generated ? generatePassword() : adminPassword;

            memberRepository.save(MemberEntity.builder()
                    .loginId(adminLoginId)
                    .passwordHash(passwordEncoder.encode(rawPassword))
                    .nickname("운영자")
                    .role(Role.ADMIN)
                    .build());

            if (generated) {
                log.warn("""

                        ============================================================
                         최초 관리자 계정이 생성되었습니다.
                           아이디   : {}
                           비밀번호 : {}
                         이 비밀번호는 지금 한 번만 출력됩니다. 반드시 기록한 뒤
                         로그인하여 마이페이지에서 변경하세요.
                        ============================================================""", adminLoginId, rawPassword);
            } else {
                log.warn("최초 관리자 계정 생성: {} (환경변수로 지정된 비밀번호 사용)", adminLoginId);
            }
        };
    }

    /** 영문 대소문자 + 숫자 조합의 16자 임의 비밀번호 */
    private String generatePassword() {
        byte[] bytes = new byte[12];
        new SecureRandom().nextBytes(bytes);
        String encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
                .replaceAll("[^a-zA-Z0-9]", "");
        // 비밀번호 정책(영문+숫자 8자 이상)을 항상 만족시키기 위해 숫자 하나를 보강
        return (encoded + "7a").substring(0, Math.min(16, encoded.length() + 2));
    }
}
