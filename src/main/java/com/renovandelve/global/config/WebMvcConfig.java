package com.renovandelve.global.config;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.resource.PathResourceResolver;
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

    /**
     * 프론트 빌드 결과(static/)를 함께 배포할 때의 정적 파일 처리.
     * 파일이 있으면 그 파일을, 없으면 index.html을 돌려줘 /game, /chronicle 같은 화면 경로를 새로고침해도 404가 나지 않게 한다.
     * /api 아래는 넘기지 않고, index.html이 없는 개발 환경(프론트를 Vite로 따로 띄움)에서는 예전처럼 404다.
     *
     * @param registry 정적 리소스 등록기
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new PathResourceResolver() {
                    @Override
                    protected Resource getResource(String resourcePath, Resource location) throws IOException {
                        Resource requested = location.createRelative(resourcePath);
                        if (requested.exists() && requested.isReadable()) return requested;
                        if (resourcePath.startsWith("api/")) return null;
                        Resource index = location.createRelative("index.html");
                        return index.exists() && index.isReadable() ? index : null;
                    }
                });
    }
}
