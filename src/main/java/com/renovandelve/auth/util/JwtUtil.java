package com.renovandelve.auth.util;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.Map;

@Slf4j
@Component
public class JwtUtil {

    @Value("${renovaNDelve.jwt.secret}")
    private String key;

    /**
     * 서명키 생성
     * @return 서명키 시크릿키로 변환값
     */
    private SecretKey getSecretKey() {
        return Keys.hmacShaKeyFor(key.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 토큰 발권
     * @param valueMap PayLoad 사용자 데이터가 들어감
     * @param minutes 토큰 기간을 정하는 분 변수
     * @return 기간이 정해진 토큰 발권
     */
    public String generateToken(Map<String, Object> valueMap, long minutes) {
        return Jwts.builder()
                .header()
                    .type("JWT") // 헤더에 타입 지정
                    .and()
                .claims(valueMap) // 토큰 저장
                .issuedAt(Date.from(LocalDateTime.now().atZone(ZoneId.systemDefault()).toInstant())) // 로컬 기준으로 지정
                .expiration(Date.from(LocalDateTime.now().plusMinutes(minutes).atZone(ZoneId.systemDefault()).toInstant())) // 만료일 지정
                .signWith(getSecretKey())
                .compact();
    }

    /**
     * 토큰 검증 (위조인지 정상 발권인지)
     * @param token AccessToken, RefreshToken
     * @return PayLoad 데이터
     * @throws JwtException 서명 불일치, 만료 등 토큰이 유효하지 않을 경우 예외
     */
    public Map<String, Object> validateToken(String token) throws JwtException {
        return Jwts.parser()
                .verifyWith(getSecretKey()) // 서명키 검증
                .build()
                .parseSignedClaims(token) // PayLoad 검증
                .getPayload(); // 사용
    }
}
