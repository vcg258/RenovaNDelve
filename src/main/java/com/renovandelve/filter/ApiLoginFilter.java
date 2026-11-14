package com.renovandelve.filter;

import com.google.gson.Gson;
import com.renovandelve.handler.LoginAttemptService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

@Slf4j
public class ApiLoginFilter extends AbstractAuthenticationProcessingFilter {
    private final LoginAttemptService loginAttemptService;

    public ApiLoginFilter(String defaultFilterProcessesUrl, LoginAttemptService loginAttemptService) {
        super(defaultFilterProcessesUrl);
        this.loginAttemptService = loginAttemptService;
    }

    /**
     * JSON 본문의 아이디·비밀번호로 로그인한다. 계정+IP 단위로 잠겨 있으면 인증 전에 거부한다.
     *
     * @return 인증 결과
     * @throws AuthenticationException 잠김, 형식 오류, 인증 실패
     */
    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException, IOException, ServletException {
        if (request.getMethod().equalsIgnoreCase("GET")) {
            throw new AuthenticationServiceException("GET 메서드는 지원하지 않습니다.");
        }

        Map<String, Object> jsonData = parseRequestJson(request);
        if (jsonData == null) {
            throw new AuthenticationServiceException("요청 데이터 없음");
        }

        String loginId = String.valueOf(jsonData.get("loginId"));
        // 실패 핸들러가 대상 계정을 알 수 있도록 요청 속성에 보관
        request.setAttribute("loginId", loginId);

        // 실패 횟수는 "계정 + 접속 IP" 단위로 센다.
        // 계정 단위로만 세면 공격자가 남의 아이디로 일부러 5번 틀려 정상 사용자를 잠글 수 있다(계정 잠금 DoS).
        String attemptKey = LoginAttemptService.attemptKey(loginId, request.getRemoteAddr());
        request.setAttribute(LoginAttemptService.ATTEMPT_KEY_ATTRIBUTE, attemptKey);

        // 무차별 대입 차단: 임계치 초과 시 비밀번호 검증 자체를 시도하지 않는다
        if (loginAttemptService.isBlocked(attemptKey)) {
            throw new LockedException("로그인 시도 횟수 초과");
        }

        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(loginId, jsonData.get("password"));

        return getAuthenticationManager().authenticate(authenticationToken);
    }

    /**
     * Json 직렬화 역직렬화
     * @param request 요청값
     * @return Json <-> Map
     */
    private Map<String, Object> parseRequestJson(HttpServletRequest request) {
        try (Reader reader = new InputStreamReader(request.getInputStream())) {
            return new Gson().fromJson(reader, Map.class);
        } catch (IOException e) {
            log.error("{}", e.getMessage());
        }
        return null;
    }
}
