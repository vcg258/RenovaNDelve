package com.renovandelve.filter;

import com.google.gson.Gson;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

@Log4j2
public class ApiLoginFilter extends AbstractAuthenticationProcessingFilter {
    public ApiLoginFilter(String defaultFilterProcessesUrl) {
        super(defaultFilterProcessesUrl);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response) throws AuthenticationException, IOException, ServletException {
        log.info("로그인 검증 메서드 진입");
        if (request.getMethod().equalsIgnoreCase("GET")) {
            log.error("GET 접근 불가능");
            throw new AuthenticationServiceException("GET 메서드는 지원하지 않습니다.");
        }

        Map<String, Object> jsonData = parseRequestJson(request);
        if (jsonData == null) {
            throw new AuthenticationServiceException("요청 데이터 없음");
        }
        log.info("API JSON 로그인 데이터 = {}", jsonData);

        // 아직 인증되지 않은 토큰 생성
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(
                        jsonData.get("loginId"),
                        jsonData.get("password")
                );
        // 검증매니저에게 검증
        return getAuthenticationManager().authenticate(authenticationToken);
    }

    /**
     * Json 직렬화 역직렬화
     * @param request 요청값
     * @return Json <-> Map
     */
    private Map<String, Object> parseRequestJson(HttpServletRequest request) {
        try (Reader reader = new InputStreamReader(request.getInputStream())) {
            Gson gson = new Gson();
            return gson.fromJson(reader, Map.class);
        } catch (IOException e) {
            log.error(e.getMessage());
        }
        return null;
    }
}
