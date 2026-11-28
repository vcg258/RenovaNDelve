package com.renovandelve.global.exception;

import org.springframework.http.HttpStatus;

/**
 * 비즈니스 오류 목록. 응답의 상태 코드와 사용자에게 보일 문구를 한곳에서 정한다.
 * 문구에 {@code %s}, {@code %d}가 있으면 {@link BusinessException}을 만들 때 넘긴 값으로 채운다.
 * 응답 본문은 {@code {"code": 이름, "error": 문구}}다.
 */
public enum ErrorCode {

    /* ===== 공통 ===== */
    /** 형식은 맞지만 받아들일 수 없는 요청. 문구를 그대로 쓴다 */
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "%s"),
    AUTHENTICATION_REQUIRED(HttpStatus.UNAUTHORIZED, "인증 정보가 없습니다."),
    CONCURRENT_ACTION(HttpStatus.CONFLICT, "이전 행동을 처리하는 중입니다. 잠시 후 다시 시도해주세요."),

    /* ===== 회원 ===== */
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "회원을 찾을 수 없습니다."),
    LOGIN_ID_TAKEN(HttpStatus.BAD_REQUEST, "이미 사용 중인 아이디입니다."),
    EMAIL_TAKEN(HttpStatus.BAD_REQUEST, "이미 사용 중인 이메일입니다."),
    NICKNAME_TAKEN(HttpStatus.BAD_REQUEST, "이미 사용 중인 닉네임입니다."),
    /** 중복 확인과 저장 사이에 같은 값으로 동시에 가입한 경우 (DB 유니크 제약) */
    SIGNUP_CONFLICT(HttpStatus.BAD_REQUEST, "이미 사용 중인 아이디, 이메일 또는 닉네임입니다."),
    NICKNAME_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "사용할 수 없는 닉네임입니다."),
    NICKNAME_UNCHANGED(HttpStatus.BAD_REQUEST, "현재 닉네임과 동일합니다."),
    PASSWORD_MISMATCH(HttpStatus.BAD_REQUEST, "현재 비밀번호가 일치하지 않습니다."),
    PASSWORD_UNCHANGED(HttpStatus.BAD_REQUEST, "현재 비밀번호와 다른 비밀번호를 사용해주세요."),
    SOCIAL_PASSWORD_UNSUPPORTED(HttpStatus.CONFLICT, "소셜 로그인 계정은 비밀번호를 변경할 수 없습니다."),
    RESET_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "재설정 링크가 만료되었거나 이미 사용되었습니다. 비밀번호 찾기를 다시 진행해주세요."),

    /* ===== 관리자 ===== */
    /** 관리자가 자기 자신에게 할 수 없는 조치. 문구는 조치마다 다르다 */
    ADMIN_SELF_ACTION(HttpStatus.CONFLICT, "%s"),
    WITHDRAW_SELF_ONLY(HttpStatus.BAD_REQUEST, "탈퇴 처리는 회원 본인만 할 수 있습니다."),
    ALREADY_WITHDRAWN(HttpStatus.CONFLICT, "이미 탈퇴한 회원입니다."),
    ADMIN_CANNOT_BE_SUSPENDED(HttpStatus.CONFLICT, "관리자 계정은 정지할 수 없습니다. 권한을 먼저 회수해주세요."),
    WITHDRAWN_ROLE_LOCKED(HttpStatus.CONFLICT, "탈퇴한 회원의 권한은 변경할 수 없습니다."),
    LAST_ADMIN(HttpStatus.CONFLICT, "마지막 관리자는 강등할 수 없습니다."),
    UNKNOWN_ROLE(HttpStatus.BAD_REQUEST, "알 수 없는 권한입니다: %s"),
    STATUS_REQUIRED(HttpStatus.BAD_REQUEST, "상태를 지정해주세요."),
    UNKNOWN_STATUS(HttpStatus.BAD_REQUEST, "알 수 없는 상태입니다: %s"),

    /* ===== 원정 ===== */
    GAME_NOT_FOUND(HttpStatus.NOT_FOUND, "진행 중인 게임이 없습니다."),
    GAME_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "게임 기록이 없습니다."),
    /** 지금 단계에서는 할 수 없는 행동. 첫 값은 안내 문구, 둘째 값은 현재 단계 */
    INVALID_PHASE(HttpStatus.CONFLICT, "%s (현재 단계: %s)"),
    UNKNOWN_DIFFICULTY(HttpStatus.BAD_REQUEST, "알 수 없는 난이도: %s"),
    UNKNOWN_PACT(HttpStatus.BAD_REQUEST, "알 수 없는 서약: %s"),
    BUDGET_EXCEEDED(HttpStatus.BAD_REQUEST, "스타팅 포인트 예산 초과: 사용 %d / 예산 %d"),
    /** 원정 준비에서 아직 해금하지 않은 희귀·영웅 항목을 골랐다 */
    SETUP_ITEM_LOCKED(HttpStatus.BAD_REQUEST, "%s은(는) 아직 해금되지 않았습니다. 원정에서 한 번 손에 넣으면 도감에 등록되어 고를 수 있습니다."),
    NOT_ENOUGH_GOLD(HttpStatus.BAD_REQUEST, "골드가 부족합니다. 보유: %d, 필요: %d"),
    /** 상점·보상에서 고른 품목이 카탈로그에 없다. 문구는 품목 종류마다 다르다 */
    ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "%s"),
    SHOP_ITEM_UNAVAILABLE(HttpStatus.BAD_REQUEST, "상점에 없는 아이템이거나 이미 판매된 상품입니다."),
    INVALID_CHOICE(HttpStatus.BAD_REQUEST, "잘못된 선택지입니다."),

    /* ===== 스킬·유물·소모품 ===== */
    SKILL_SLOT_FULL(HttpStatus.CONFLICT, "스킬 슬롯이 가득 찼습니다. 버릴 스킬을 골라주세요. (최대 %d개)"),
    BASIC_SKILL_LOCKED(HttpStatus.CONFLICT, "기본 공격은 스킬이 %d개 이상일 때만 버릴 수 있습니다."),
    LAST_SKILL(HttpStatus.CONFLICT, "마지막 남은 스킬은 버릴 수 없습니다."),
    SKILL_ALREADY_OWNED(HttpStatus.CONFLICT, "이미 보유 중인 스킬입니다."),
    SKILL_SLOT_NOT_OWNED(HttpStatus.BAD_REQUEST, "보유하지 않은 스킬 슬롯입니다. slotId=%s"),
    SKILL_ON_COOLDOWN(HttpStatus.CONFLICT, "쿨타임이 남아 있습니다. (%d턴)"),
    SKILL_DISCARD_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 스킬을 버릴 수 없습니다. (현재 단계: %s)"),
    RELIC_ALREADY_OWNED(HttpStatus.CONFLICT, "이미 보유 중인 유물입니다."),
    CONSUMABLE_NOT_OWNED(HttpStatus.BAD_REQUEST, "보유하지 않은 소모품입니다."),
    CONSUMABLE_NOT_ALLOWED(HttpStatus.CONFLICT, "지금은 소모품을 사용할 수 없습니다."),

    /* ===== 전투 ===== */
    PASS_NOT_ALLOWED(HttpStatus.CONFLICT, "쓸 수 있는 스킬이 남아 있습니다. 숨 고르기는 모든 스킬이 쿨타임일 때만 할 수 있습니다."),
    FLEE_NOT_ALLOWED(HttpStatus.CONFLICT, "이 구역의 지배자에게서는 달아날 수 없습니다."),

    /* ===== 악마의 계약 ===== */
    NO_CONTRACT(HttpStatus.CONFLICT, "걸린 계약이 없습니다."),
    CONTRACT_BREAK_LOCKED(HttpStatus.CONFLICT, "천상의 성향이 %d 이상이어야 계약을 파기할 수 있습니다.");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    /** @return 응답 상태 코드 */
    public HttpStatus status() {
        return status;
    }

    /**
     * @param args 문구의 빈칸에 넣을 값
     * @return 사용자에게 보일 문구
     */
    public String message(Object... args) {
        return args.length == 0 ? message : message.formatted(args);
    }
}
