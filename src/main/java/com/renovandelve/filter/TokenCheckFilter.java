package com.renovandelve.filter;

import com.renovandelve.exception.AccessTokenException;
import com.renovandelve.exception.enums.TokenError;
import com.renovandelve.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Log4j2
@RequiredArgsConstructor
public class TokenCheckFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/api/auth/")
//                || path.endsWith(".html") // TODO OAuth2 테스트 용
//                || path.startsWith("/api/auth/refresh")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/oauth2")
                || path.startsWith("/login/oauth2")) {
            filterChain.doFilter(request, response);
            return;
        }

        log.info("TokenCheckFilter 접근");

        try {
            Map<String, Object> claims = validateAccessToken(request);

            String loginId = (String) claims.get("loginId");
            log.info("로그인 시도 : {}", loginId);

            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    loginId,
                    null,
                    List.of()
            );

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);

            filterChain.doFilter(request, response);
        } catch (AccessTokenException e) {
            e.sendResponseError(response);
        }

    }

    // AccessToken 검증
    private Map<String, Object> validateAccessToken(HttpServletRequest request) {
        String headerStr = request.getHeader("Authorization");

        // 헤더 없거나 8자 미만이다 401
        if (headerStr == null || headerStr.length() < 8) {
            throw new AccessTokenException(TokenError.UNACCEPT);
        }
        String tokenType = headerStr.substring(0, 6); // 해더 추출
        String tokenStr = headerStr.substring(7); // 해더 제외

        // 토큰타입이 Bearer가 아니다 401
        if (!tokenType.equals("Bearer")) {
            throw new AccessTokenException(TokenError.BADTYPE);
        }
        try {
            // 토큰 정상적이다 반환
            return jwtUtil.validateToken(tokenStr);
        } catch (MalformedJwtException malformedJwtException) {
            // 형식이 잘못됨 403
            log.error("MalformedJwtException : {}", malformedJwtException.getMessage());
            throw new AccessTokenException(TokenError.MALFORM);
        } catch (SignatureException signatureException) {
            // 서명이 위조됨 403
            log.error("SignatureException : {}", signatureException.getMessage());
            throw new AccessTokenException(TokenError.BADSIGN);
        } catch (ExpiredJwtException expiredJwtException) {
            // 만료 403
            log.error("ExpiredJwtException : {}", expiredJwtException.getMessage());
            throw new AccessTokenException(TokenError.EXPIRED);
        }
    }
}
