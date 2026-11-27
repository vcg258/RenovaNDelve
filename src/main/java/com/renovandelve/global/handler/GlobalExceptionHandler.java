package com.renovandelve.global.handler;

import org.springframework.dao.DataIntegrityViolationException;
import com.renovandelve.global.exception.BusinessException;
import com.renovandelve.global.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 전역 예외 처리.
 * 응답은 {"code": "오류 코드", "error": "문구"}로 통일한다. 화면은 문구(error)를 보여 주고, 분기가 필요하면 code를 본다.
 * 비즈니스 오류는 {@link BusinessException}만 쓴다. 그 밖의 런타임 예외는 500이다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** Bean Validation 실패 -> 첫 번째 필드 메시지를 사용자에게 그대로 노출 */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));

        String message = fieldErrors.values().stream().findFirst().orElse("입력값을 확인해주세요.");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", "INVALID_INPUT");
        body.put("error", message);
        body.put("fields", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    /** 본문이 없거나 JSON 형식이 잘못된 요청 */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, String>> handleUnreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(body("INVALID_INPUT", "요청 형식이 올바르지 않습니다."));
    }

    /**
     * 비즈니스 규칙 위반 -> {@link ErrorCode}에 적힌 상태 코드와 문구.
     *
     * @param e 예외
     * @return 오류 코드와 문구
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, String>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.status()).body(body(code.name(), e.getMessage()));
    }

    /** 경로·쿼리 값의 타입이 맞지 않는 요청 (숫자 자리에 글자 등) */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, String>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity.badRequest().body(body("INVALID_INPUT", "요청 값의 형식이 올바르지 않습니다: " + e.getName()));
    }

    /**
     * DB 유니크 제약 위반 -> 409. 확인과 저장 사이에 같은 요청이 겹친 경우라 데이터는 지켜졌고, 다시 조회하면 된다.
     *
     * @param e 예외
     * @return 409와 안내 문구
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> handleDataIntegrity(DataIntegrityViolationException e) {
        log.warn("DB 제약 위반: {}", e.getMostSpecificCause().getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body("DUPLICATE_REQUEST", "이미 처리된 요청입니다. 화면을 새로 고쳐 주세요."));
    }

    /** 권한 부족 */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body("ACCESS_DENIED", "접근 권한이 없습니다."));
    }

    /**
     * 그 외 예외는 모두 예상하지 못한 서버 오류로 본다. 자바·스프링·JPA 예외의 내부 문구는 클라이언트에 보내지 않고 로그에만 남긴다.
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntime(RuntimeException e) {
        log.error("처리되지 않은 예외", e);
        return ResponseEntity.internalServerError()
                .body(body("INTERNAL_ERROR", "요청 처리 중 오류가 발생했습니다."));
    }

    /**
     * @param code    오류 코드 (화면이 분기할 때 쓴다)
     * @param message 사용자에게 보일 문구
     * @return {@code {"code": ..., "error": ...}}
     */
    private static Map<String, String> body(String code, String message) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("error", message);
        return body;
    }
}
