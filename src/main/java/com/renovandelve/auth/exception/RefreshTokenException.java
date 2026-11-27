package com.renovandelve.auth.exception;

import com.google.gson.Gson;
import com.renovandelve.auth.exception.enums.ErrorCase;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

@Slf4j
public class RefreshTokenException extends RuntimeException {
    private final ErrorCase errorCase;

    public RefreshTokenException(ErrorCase errorCase) {
        super(errorCase.name());
        this.errorCase = errorCase;
    }

    /**
     * RefreshToken 예외처리 발생
     * @param response 응답값
     */
    public void sendResponseError(HttpServletResponse response) {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Gson gson = new Gson();
        String responseStr = gson.toJson(Map.of("msg", errorCase.name(), "date", new Date()));
        try {
            response.getWriter().println(responseStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
