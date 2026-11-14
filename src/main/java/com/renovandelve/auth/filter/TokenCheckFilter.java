package com.renovandelve.auth.filter;

import com.renovandelve.auth.exception.AccessTokenException;
import com.renovandelve.auth.exception.enums.TokenError;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import com.renovandelve.auth.util.JwtUtil;
import com.renovandelve.auth.util.TokenClaims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public class TokenCheckFilter extends OncePerRequestFilter {
    private final JwtUtil jwtUtil;
    private final TokenRevocationService tokenRevocationService;

    /**
     * 인증이 필요한 요청의 AccessToken을 검사해 SecurityContext에 인증 정보를 넣는다.
     * 용도가 RefreshToken이거나, 정지·권한 변경 등으로 폐기된 토큰이면 거부한다.
     */
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

            // 정지·권한 변경·탈퇴·로그아웃 이전에 발급된 토큰은 만료 전이라도 받지 않는다.
            // (만료로 응답하면 프론트가 재발급을 시도하고, 그 시점에 DB의 계정 상태·권한이 다시 확인된다)
            long issuedAt = claims.get("iat") instanceof Number number ? number.longValue() : 0L;
            if (tokenRevocationService.isRevoked(loginId, issuedAt)) {
                throw new AccessTokenException(TokenError.EXPIRED);
            }

            // 토큰의 role 클레임을 그대로 authority로 사용 -> 요청마다 DB 조회 없이 인가 판정
            // (권한이 바뀌면 위 폐기 검사에 걸려 재발급을 거치므로 낡은 role이 남지 않는다)
            UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                    loginId, null, List.of(new SimpleGrantedAuthority("ROLE_" + role)));

            SecurityContextHolder.getContext().setAuthentication(authenticationToken);
            filterChain.doFilter(request, response);
        } catch (AccessTokenException e) {
            e.sendResponseError(response);
        }
    }

    // AccessToken 검증
    /**
     * Authorization 헤더에서 Bearer 토큰을 꺼내 서명과 만료를 검증한다.
     *
     * @param request 요청
     * @return 토큰의 클레임
     * @throws com.renovandelve.auth.exception.AccessTokenException 헤더가 없거나 형식·서명·만료가 잘못되면
     */
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
