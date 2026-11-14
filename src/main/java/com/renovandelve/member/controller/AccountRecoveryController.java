package com.renovandelve.member.controller;

import com.renovandelve.member.dto.request.FindIdRequest;
import com.renovandelve.member.dto.request.PasswordResetCheckRequest;
import com.renovandelve.member.dto.request.PasswordResetConfirmRequest;
import com.renovandelve.member.dto.request.PasswordResetRequest;
import com.renovandelve.member.service.AccountRecoveryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 아이디 찾기 / 비밀번호 재설정 (로그인 전 API).
 *
 * 두 요청 API는 입력이 실제 계정과 맞든 아니든 항상 같은 응답을 돌려준다.
 * 응답이 다르면 "이 이메일로 가입한 사람이 있는가"를 누구나 확인할 수 있게 된다.
 * 요청 제한은 AuthRateLimitFilter에서 IP 단위로 건다.
 */
@Tag(name = "계정 찾기", description = "아이디 찾기 / 비밀번호 재설정")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/auth")
public class AccountRecoveryController {
    static final String FIND_ID_MESSAGE = "입력하신 정보와 일치하는 계정이 있으면 해당 이메일로 아이디를 보내드렸습니다.";
    static final String RESET_MESSAGE = "입력하신 정보와 일치하는 계정이 있으면 해당 이메일로 재설정 링크를 보내드렸습니다.";

    private final AccountRecoveryService accountRecoveryService;

    /**
     * 아이디 찾기. 이름과 이메일이 맞으면 가입한 이메일로 아이디를 보낸다.
     * 계정 유무가 드러나지 않도록 응답은 항상 같다.
     *
     * @param request 이름, 이메일
     * @return 안내 문구
     */
    @Operation(summary = "아이디 찾기", description = "이름과 이메일이 맞으면 가입된 이메일로 아이디를 보냅니다. 응답은 항상 같습니다.")
    @PostMapping("/find-id")
    public ResponseEntity<Map<String, String>> findId(@Valid @RequestBody FindIdRequest request) {
        accountRecoveryService.sendLoginId(request.name(), request.email());
        return ResponseEntity.ok(Map.of("message", FIND_ID_MESSAGE));
    }

    /**
     * 비밀번호 재설정 링크를 요청한다. 아이디와 이메일이 맞으면 1회용 링크를 메일로 보낸다.
     * 응답은 항상 같다.
     *
     * @param request 아이디, 이메일
     * @return 안내 문구
     */
    @Operation(summary = "비밀번호 재설정 링크 요청", description = "아이디와 이메일이 맞으면 1회용 링크를 보냅니다. 응답은 항상 같습니다.")
    @PostMapping("/password-reset/request")
    public ResponseEntity<Map<String, String>> requestReset(@Valid @RequestBody PasswordResetRequest request) {
        accountRecoveryService.requestPasswordReset(request.loginId(), request.email());
        return ResponseEntity.ok(Map.of("message", RESET_MESSAGE));
    }

    /**
     * 재설정 링크가 아직 유효한지 확인한다. 토큰을 소비하지 않는다.
     *
     * @param request 링크의 토큰
     * @return valid 여부
     */
    @Operation(summary = "재설정 링크 유효 여부", description = "토큰을 소비하지 않습니다. 재설정 화면이 만료 여부를 먼저 알려주는 데 씁니다.")
    @PostMapping("/password-reset/check")
    public ResponseEntity<Map<String, Boolean>> checkReset(@Valid @RequestBody PasswordResetCheckRequest request) {
        return ResponseEntity.ok(Map.of("valid", accountRecoveryService.isResetTokenValid(request.token())));
    }

    /**
     * 새 비밀번호로 바꾼다. 토큰은 한 번만 쓸 수 있고, 성공하면 모든 기기에서 로그아웃된다.
     *
     * @param request 토큰, 새 비밀번호
     * @return 204
     */
    @Operation(summary = "비밀번호 재설정", description = "링크의 토큰은 한 번만 쓸 수 있습니다. 성공하면 모든 기기에서 로그아웃됩니다.")
    @PostMapping("/password-reset/confirm")
    public ResponseEntity<Void> confirmReset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        accountRecoveryService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    }
}
