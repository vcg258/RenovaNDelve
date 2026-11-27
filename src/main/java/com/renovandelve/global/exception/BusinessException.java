package com.renovandelve.global.exception;

/**
 * 비즈니스 규칙 위반. 서비스·엔티티는 이 예외만 던지고, 응답은 {@code GlobalExceptionHandler}가 만든다.
 * 그 밖의 예외(자바·스프링·JPA 예외)는 예상하지 못한 오류로 보고 500으로 응답한다.
 */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    /**
     * @param errorCode 오류
     * @param args      문구의 빈칸에 넣을 값
     */
    public BusinessException(ErrorCode errorCode, Object... args) {
        super(errorCode.message(args));
        this.errorCode = errorCode;
    }

    /** @return 오류 */
    public ErrorCode getErrorCode() {
        return errorCode;
    }
}
