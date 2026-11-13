package com.renovandelve.filter;

import com.renovandelve.exception.AccessTokenException;
import com.renovandelve.exception.enums.TokenError;
import com.renovandelve.util.JwtUtil;
import com.renovandelve.util.TokenClaims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
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
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String path = request.getRequestURI();

        if (path.startsWith("/api/auth/")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/oauth2")
                || path.startsWith("/login/oauth2")) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            Map<String, Object> claims = validateAccessToken(request);

            // RefreshToken(수명 1일)을 Authorization 헤더에 넣어 AccessToken처럼 쓰는 것을 차단
            if (!TokenClaims.isAccess(claims)) {
                throw new AccessTokenException(TokenError.BADTYPE);
            }

            String loginId = (String) claims.get("loginId");
            String role = (String) claims.getOrDefault("role", "USER");

            // 토큰의 role 클레임을 그대로 authority로 사용 -> 요청마다 DB 조회 없이 인가 판정
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    loginId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (AccessTokenException e) {
            e.sendResponseError(response);
        }
    }

    // AccessToken 검증
    private Map<String, Object> validateAccessToken(HttpServletRequest request) {
        String headerStr = request.getHeader("Authorization");

        if (headerStr == null || headerStr.length() < 8) {
            throw new AccessTokenException(TokenError.UNACCEPT);
        }
        String tokenType = headerStr.substring(0, 6);
        String tokenStr = headerStr.substring(7);

        if (!tokenType.equals("Bearer")) {
            throw new AccessTokenException(TokenError.BADTYPE);
        }
        try {
            return jwtUtil.validateToken(tokenStr);
        } catch (MalformedJwtException e) {
            log.error("MalformedJwtException : {}", e.getMessage());
            throw new AccessTokenException(TokenError.MALFORM);
        } catch (SignatureException e) {
            log.error("SignatureException : {}", e.getMessage());
            throw new AccessTokenException(TokenError.BADSIGN);
        } catch (ExpiredJwtException e) {
            // 만료는 30분마다 발생하는 정상 흐름이므로 error 레벨로 남기지 않는다
            log.debug("AccessToken 만료");
            throw new AccessTokenException(TokenError.EXPIRED);
        } catch (JwtException | IllegalArgumentException e) {
            // 지원하지 않는 형식, 빈 토큰 등 -> 500이 아니라 토큰 오류로 응답
            log.warn("AccessToken 검증 실패 : {}", e.getClass().getSimpleName());
            throw new AccessTokenException(TokenError.MALFORM);
        }
    }
}
