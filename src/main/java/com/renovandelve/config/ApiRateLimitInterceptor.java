package com.renovandelve.config;

import com.renovandelve.service.redis.RateLimitService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Duration;

/**
 * LLM을 호출하는 API의 회원 단위 요청 제한.
 *
 * 가이드 챗봇 1회, 노드 전진 1회가 각각 OpenAI 호출 여러 번으로 이어지므로
 * 제한이 없으면 로그인한 사용자 한 명이 반복 호출만으로 API 비용을 무제한 발생시킬 수 있다.
 */
@Component
@RequiredArgsConstructor
public class ApiRateLimitInterceptor implements HandlerInterceptor {
    private final RateLimitService rateLimitService;

    private record Rule(String bucket, int limit, Duration window, String message) {
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!"POST".equalsIgnoreCase(request.getMethod())) return true;

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) return true;
        String subject = authentication.getName();

        for (Rule rule : resolveRules(request.getRequestURI())) {
            long retryAfter = rateLimitService.retryAfterSeconds(rule.bucket(), subject, rule.limit(), rule.window());
            if (retryAfter > 0) {
                response.setStatus(429);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().println("{\"error\": \"" + rule.message() + "\"}");
                return false;
            }
        }
        return true;
    }

    private Rule[] resolveRules(String path) {
        return switch (path) {
            case "/api/rag" -> new Rule[]{
                    new Rule("guide:min", 8, Duration.ofMinutes(1), "안내자가 숨을 고르는 중입니다. 잠시 후 다시 물어보세요."),
                    new Rule("guide:day", 150, Duration.ofDays(1), "오늘은 안내자에게 물을 수 있는 횟수를 모두 사용했습니다.")};
            case "/api/game/next" -> new Rule[]{
                    new Rule("node:min", 20, Duration.ofMinutes(1), "너무 빠르게 전진하고 있습니다. 잠시 후 다시 시도해주세요.")};
            case "/api/game/new" -> new Rule[]{
                    new Rule("newgame", 10, Duration.ofMinutes(10), "새 게임을 너무 자주 시작하고 있습니다. 잠시 후 다시 시도해주세요.")};
            default -> new Rule[0];
        };
    }
}
