package com.renovandelve.global.handler;

import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("비즈니스 오류는 ErrorCode의 상태 코드와 문구로, 문구의 빈칸은 넘긴 값으로 채운다")
    void businessError() {
        ResponseEntity<Map<String, String>> res = handler.handleBusiness(
                new BusinessException(ErrorCode.NOT_ENOUGH_GOLD, 30, 120));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(res.getBody()).containsEntry("code", "NOT_ENOUGH_GOLD")
                .containsEntry("error", "골드가 부족합니다. 보유: 30, 필요: 120");
    }

    @Test
    @DisplayName("자바·스프링 예외는 비즈니스 오류로 보지 않는다: 500이고 내부 문구를 보내지 않는다")
    void unexpectedErrorHidesMessage() {
        ResponseEntity<Map<String, String>> res = handler.handleRuntime(
                new IllegalStateException("Transaction silently rolled back because it has been marked as rollback-only"));

        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(res.getBody()).containsEntry("code", "INTERNAL_ERROR");
        assertThat(res.getBody().get("error")).doesNotContain("rollback");
    }

    @Test
    @DisplayName("모든 ErrorCode는 4xx이고 문구가 비어 있지 않다")
    void errorCodesAreClientErrors() {
        for (ErrorCode code : ErrorCode.values()) {
            assertThat(code.status().is4xxClientError()).as(code.name()).isTrue();
            assertThat(code.message()).as(code.name()).isNotBlank();
        }
    }
}
