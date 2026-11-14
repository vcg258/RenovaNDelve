package com.renovandelve.handler;

import com.google.gson.Gson;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;

import java.io.IOException;
import java.util.Map;

/**
 * 로그인 실패 응답.
 * 계정 존재 여부가 드러나지 않도록 메시지를 통일하되, 정지 계정과 시도 제한은 명확히 안내한다.
 */
@Slf4j
@RequiredArgsConstructor
public class LoginFailureHandler implements AuthenticationFailureHandler {
    private final LoginAttemptService loginAttemptService;

    /**
     * 로그인 실패 응답을 만든다. 정지 계정, 잠김, 일반 실패(남은 횟수 안내)를 구분해 401로 알린다.
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {
        String loginId = (String) request.getAttribute("loginId");
        String attemptKey = (String) request.getAttribute(LoginAttemptService.ATTEMPT_KEY_ATTRIBUTE);
        String message;

        if (exception instanceof DisabledException) {
            message = "이용이 제한된 계정입니다. 운영자에게 문의해주세요.";
        } else if (loginAttemptService.isBlocked(attemptKey)) {
            message = "로그인 시도 횟수를 초과했습니다. %d분 후 다시 시도해주세요.".formatted(loginAttemptService.blockMinutes());
        } else {
            loginAttemptService.loginFailed(attemptKey);
            if (loginAttemptService.isBlocked(attemptKey)) {
                message = "로그인 시도 횟수를 초과했습니다. %d분 후 다시 시도해주세요.".formatted(loginAttemptService.blockMinutes());
            } else {
                message = "아이디 또는 비밀번호가 올바르지 않습니다. (남은 시도 %d회)"
                        .formatted(loginAttemptService.remainingAttempts(attemptKey));
            }
        }

        log.info("로그인 실패 loginId={} 원인={}", loginId, exception.getClass().getSimpleName());

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().println(new Gson().toJson(Map.of("error", message)));
    }
}
