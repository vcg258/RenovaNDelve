package com.renovandelve.global.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * 운영 프로파일(prod) 없이 기동했을 때 로그에 경고를 남긴다.
 * prod를 빠뜨려도 서버는 개발 설정으로 오류 없이 뜨기 때문에, 배포 후 로그에서 바로 알아챌 수 있게 한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileWarning {

    private final Environment environment;

    /**
     * 기동이 끝나면 활성 프로파일을 확인한다.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warnIfNotProduction() {
        if (environment.acceptsProfiles(Profiles.of("prod"))) {
            log.info("운영 프로파일(prod)로 기동했습니다.");
            return;
        }
        log.warn("""
                운영 프로파일(prod) 없이 개발 설정으로 기동했습니다.
                이 상태에서는 Swagger가 열려 있고, 로그인 쿠키의 Secure가 꺼져 있으며, DB 접속에 개발용 기본값을 씁니다.
                실제 서비스라면 --spring.profiles.active=prod 로 다시 실행하세요.""");
    }
}
