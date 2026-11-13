package com.renovandelve.filter;

import com.renovandelve.service.redis.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 인증 전 API(/api/auth/**)의 IP 단위 요청 제한.
 * 로그인은 MVC 컨트롤러가 아니라 Security 필터에서 처리되므로 인터셉터가 아닌 필터로 건다.
 *
 * 접속 IP는 request.getRemoteAddr()를 쓴다. 리버스 프록시 뒤에 배포할 때는
 * server.forward-headers-strategy 설정으로 X-Forwarded-For가 반영되게 해야 하며,
 * 헤더를 직접 읽지 않는 이유는 클라이언트가 임의로 위조할 수 있기 때문이다.
 */
@RequiredArgsConstructor
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final RateLimitService rateLimitService;

    private record Rule(String bucket, int limit, Duration window) {
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Rule rule = resolveRule(request.getRequestURI());
        if (rule != null) {
            long retryAfter = rateLimitService.retryAfterSeconds(
                    rule.bucket(), request.getRemoteAddr(), rule.limit(), rule.window());
            if (retryAfter > 0) {
                response.setStatus(429);
                response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");
                response.getWriter().println("{\"error\": \"요청이 너무 많습니다. 잠시 후 다시 시도해주세요.\"}");
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private Rule resolveRule(String path) {
        return switch (path) {
            case "/api/auth/login" -> new Rule("login", 10, Duration.ofMinutes(1));
            case "/api/auth/signup" -> new Rule("signup", 5, Duration.ofMinutes(10));
            // 중복 확인은 입력 중 디바운스 호출이 잦으므로 넉넉히 두되, 아이디 대량 수집은 막는다
            case "/api/auth/check-login-id", "/api/auth/check-nickname" -> new Rule("check", 40, Duration.ofMinutes(1));
            case "/api/auth/refresh" -> new Rule("refresh", 30, Duration.ofMinutes(1));
            default -> null;
        };
    }
}
