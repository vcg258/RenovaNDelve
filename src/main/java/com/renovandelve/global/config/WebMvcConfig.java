package com.renovandelve.global.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final ApiRateLimitInterceptor apiRateLimitInterceptor;

    /**
     * LLM을 부르는 경로에 회원 단위 요청 제한 인터셉터를 건다.
     *
     * @param registry 인터셉터 등록기
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiRateLimitInterceptor)
                .addPathPatterns("/api/rag", "/api/game/next", "/api/game/new");
    }
}
