package com.renovandelve.controller;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.request.NicknameChangeRequest;
import com.renovandelve.dto.request.PasswordChangeRequest;
import com.renovandelve.dto.request.SignupRequest;
import com.renovandelve.dto.response.MemberProfileDTO;
import com.renovandelve.service.CurrentMemberService;
import com.renovandelve.service.MemberService;
import com.renovandelve.service.PersonalDataService;
import com.renovandelve.dto.response.GameHistoryDTO;
import com.renovandelve.dto.response.AchievementDTO;
import com.renovandelve.service.AchievementService;
import com.renovandelve.service.redis.SecurityTokenService;
import com.renovandelve.util.JwtUtil;
import com.renovandelve.util.RefreshCookieFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "회원", description = "회원가입 / 내 정보 / 비밀번호·닉네임 변경 / 탈퇴")
@Log4j2
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MemberController {
    private final MemberService memberService;
    private final CurrentMemberService currentMemberService;
    private final SecurityTokenService securityTokenService;
    private final JwtUtil jwtUtil;
    private final RefreshCookieFactory refreshCookieFactory;
    private final PersonalDataService personalDataService;
    private final AchievementService achievementService;

    @Operation(summary = "회원 가입")
    @PostMapping("/auth/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        memberService.signup(request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "아이디 중복 확인")
    @GetMapping("/auth/check-login-id")
    public ResponseEntity<Map<String, Boolean>> checkLoginId(@RequestParam String loginId) {
        return ResponseEntity.ok(Map.of("available", memberService.isLoginIdAvailable(loginId)));
    }

    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/auth/check-nickname")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(Map.of("available", memberService.isNicknameAvailable(nickname)));
    }

    @Operation(summary = "내 정보 조회")
    @GetMapping("/member/me")
    public ResponseEntity<MemberProfileDTO> me() {
        return ResponseEntity.ok(memberService.getProfile(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "내 모험 기록 (최근 30건)")
    @GetMapping("/member/history")
    public ResponseEntity<List<GameHistoryDTO>> history() {
        return ResponseEntity.ok(personalDataService.getHistory(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "내 업적", description = "모험 기록과 도감에서 계산한 달성 현황")
    @GetMapping("/member/achievements")
    public ResponseEntity<List<AchievementDTO>> achievements() {
        return ResponseEntity.ok(achievementService.list(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "내 정보 내려받기", description = "서비스가 보관 중인 내 개인정보를 JSON으로 제공합니다. (개인정보 열람권)")
    @GetMapping("/member/me/export")
    public ResponseEntity<Map<String, Object>> exportMyData() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(personalDataService.export(currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "비밀번호 변경", description = "변경 시 모든 기기에서 로그아웃됩니다.")
    @PatchMapping("/member/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        memberService.changePassword(currentMemberService.getCurrentMember(), request);
        return ResponseEntity.noContent().headers(expiredCookies()).build();
    }

    @Operation(summary = "닉네임 변경")
    @PatchMapping("/member/nickname")
    public ResponseEntity<Void> changeNickname(@Valid @RequestBody NicknameChangeRequest request) {
        memberService.changeNickname(currentMemberService.getCurrentMember(), request);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "회원 탈퇴", description = "소프트 삭제. 랭킹·도감 기록은 익명으로 보존됩니다.")
    @DeleteMapping("/member")
    public ResponseEntity<Void> withdraw() {
        memberService.withdraw(currentMemberService.getCurrentMember());
        return ResponseEntity.noContent().headers(expiredCookies()).build();
    }

    @Operation(summary = "로그아웃 (RefreshToken 폐기 + 쿠키 제거)")
    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request) {
        String refreshToken = null;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (RefreshCookieFactory.NAME.equals(cookie.getName())) {
                    refreshToken = cookie.getValue();
                    break;
                }
            }
        }
        if (refreshToken != null) {
            try {
                String loginId = (String) jwtUtil.validateToken(refreshToken).get("loginId");
                if (loginId != null) {
                    securityTokenService.logout(loginId);
                    log.info("로그아웃 처리 loginId={}", loginId);
                }
            } catch (Exception e) {
                log.warn("로그아웃 시 토큰 해석 실패: {}", e.getMessage());
            }
        }
        return ResponseEntity.noContent().headers(expiredCookies()).build();
    }

    /** RefreshToken 쿠키 즉시 만료 (현재 경로 + 이전 버전 경로 모두) */
    private HttpHeaders expiredCookies() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, refreshCookieFactory.expire());
        headers.add(HttpHeaders.SET_COOKIE, refreshCookieFactory.expireLegacy());
        return headers;
    }
}
