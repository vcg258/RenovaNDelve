package com.renovandelve.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {
    /**
     * /api/** 경로를 "REST API" 그룹으로 묶는다.
     *
     * @return API 문서 그룹
     */
    @Bean
    public GroupedOpenApi restApi() {
        /* 경로에 /api가 포함된 컨트롤러의 경우에는 REST API로 인식 */
        return GroupedOpenApi.builder()
                .pathsToMatch("/api/**")
                .group("REST API")
                .build();
    }

    /**
     * /api 밖의 경로를 "COMMON API" 그룹으로 묶는다.
     *
     * @return API 문서 그룹
     */
    @Bean
    public GroupedOpenApi commonApi() {
        /* 경로에 /api가 포함안된 컨트롤러의 경우에는 COMMON API로 인식 */
        return GroupedOpenApi.builder()
                .pathsToMatch("/**/*")
                .pathsToExclude("/api/**/*") // 제외할 경로
                .group("COMMON API")
                .build();
    }

    /**
     * API 문서에 JWT Bearer 인증 방식을 등록한다.
     *
     * @return OpenAPI 설정
     */
    @Bean
    public OpenAPI customOpenAPI() {
        // Security Scheme 정의
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP) // HTTP 타입
                .scheme("bearer") // Bearer 토큰
                .bearerFormat("JWT"); // JWT 형식

        // 보안 요구사항 추가
        SecurityRequirement securityRequirement = new SecurityRequirement()
                .addList("bearerAuth");

        return new OpenAPI().info(new Info()
                        .title("API Documentation")
                        .version("1.0")
                        .description("API documentation with JWT security"))
                .addSecurityItem(securityRequirement) // 보안 요구사항 추가
                .components(new io.swagger.v3.oas.models.Components()
                        .addSecuritySchemes("bearerAuth", securityScheme));
    }
}


