package com.renovandelve.exception;

import com.google.gson.Gson;
import com.renovandelve.exception.enums.TokenError;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.Date;
import java.util.Map;

@Log4j2
public class AccessTokenException extends RuntimeException {
    private final TokenError tokenError;

    public AccessTokenException(TokenError tokenError) {
        super(tokenError.name());
        this.tokenError = tokenError;
    }

    /**
     * AccessToken에 관한 에러 메세지
     * @param response 응답값
     */
    public void sendResponseError(HttpServletResponse response) {
        response.setStatus(tokenError.getStatus());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);

        Gson gson = new Gson();
        String responseStr = gson.toJson(Map.of("msg", tokenError.getMsg(), "date", new Date()));
        log.info("AccessTokenException : {}", responseStr);

        try {
            response.getWriter().println(responseStr);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
