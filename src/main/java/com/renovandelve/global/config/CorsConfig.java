package com.renovandelve.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
public class CorsConfig{
    /**
     * 다른 출처(포트)의 프론트가 API를 부를 수 있게 허용한다.
     * 로컬 개발은 Vite 개발 서버(5173 등)가 8080을 부르므로 필요하다.
     * 운영은 화면과 API를 같은 주소로 함께 배포하므로 비워 둔다 (application-prod.yaml). 비어 있으면 다른 출처의 요청은 모두 거부된다.
     *
     * @param allowedOrigins 허용할 출처 목록 (renovaNDelve.cors.allowed-origins)
     * @return CORS 설정
     */
    @Bean
    public CorsConfigurationSource addCorsMappings(
            @Value("${renovaNDelve.cors.allowed-origins:}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(allowedOrigins.stream().filter(origin -> !origin.isBlank()).toList());
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
                config.setAllowCredentials(true); // 쿠키 전송 허용
                config.setAllowedHeaders(List.of("*"));// 헤더 접근 허용

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}
