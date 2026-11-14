package com.renovandelve.member.controller;

import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.member.dto.request.ContactChangeRequest;
import com.renovandelve.member.dto.request.NicknameChangeRequest;
import com.renovandelve.member.dto.request.PasswordChangeRequest;
import com.renovandelve.member.dto.request.SignupRequest;
import com.renovandelve.member.dto.response.MemberProfileDTO;
import com.renovandelve.member.service.CurrentMemberService;
import com.renovandelve.member.service.MemberService;
import com.renovandelve.member.service.PersonalDataService;
import com.renovandelve.game.dto.response.GameHistoryDTO;
import com.renovandelve.game.dto.response.AchievementDTO;
import com.renovandelve.game.service.AchievementService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.auth.util.JwtUtil;
import com.renovandelve.auth.util.RefreshCookieFactory;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Tag(name = "회원", description = "회원가입 / 내 정보 / 비밀번호·닉네임 변경 / 탈퇴")
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api")
public class MemberController {
    private final MemberService memberService;
    private final CurrentMemberService currentMemberService;
    private final SecurityTokenService securityTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final JwtUtil jwtUtil;
    private final RefreshCookieFactory refreshCookieFactory;
    private final PersonalDataService personalDataService;
    private final AchievementService achievementService;

    /**
     * 회원가입. 약관·개인정보 동의와 만 14세 확인을 서버에서 검증한다.
     *
     * @param request 아이디, 비밀번호, 닉네임, 이름, 이메일, 동의 항목
     * @return 204
     */
    @Operation(summary = "회원 가입")
    @PostMapping("/auth/signup")
    public ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
        memberService.signup(request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 아이디를 쓸 수 있는지 확인한다.
     *
     * @param loginId 확인할 아이디
     * @return available 여부
     */
    @Operation(summary = "아이디 중복 확인")
    @GetMapping("/auth/check-login-id")
    public ResponseEntity<Map<String, Boolean>> checkLoginId(@RequestParam String loginId) {
        return ResponseEntity.ok(Map.of("available", memberService.isLoginIdAvailable(loginId)));
    }

    /**
     * 닉네임을 쓸 수 있는지 확인한다.
     *
     * @param nickname 확인할 닉네임
     * @return available 여부
     */
    @Operation(summary = "닉네임 중복 확인")
    @GetMapping("/auth/check-nickname")
    public ResponseEntity<Map<String, Boolean>> checkNickname(@RequestParam String nickname) {
        return ResponseEntity.ok(Map.of("available", memberService.isNicknameAvailable(nickname)));
    }

    /**
     * 내 정보.
     *
     * @return 프로필
     */
    @Operation(summary = "내 정보 조회")
    @GetMapping("/member/me")
    public ResponseEntity<MemberProfileDTO> me() {
        return ResponseEntity.ok(memberService.getProfile(currentMemberService.getCurrentMember()));
    }

    /**
     * 내 모험 기록 (최근 30건).
     *
     * @return 기록 목록
     */
    @Operation(summary = "내 모험 기록 (최근 30건)")
    @GetMapping("/member/history")
    public ResponseEntity<List<GameHistoryDTO>> history() {
        return ResponseEntity.ok(personalDataService.getHistory(currentMemberService.getCurrentMember()));
    }

    /**
     * 내 업적. 모험 기록과 도감에서 계산한다.
     *
     * @return 업적 목록
     */
    @Operation(summary = "내 업적", description = "모험 기록과 도감에서 계산한 달성 현황")
    @GetMapping("/member/achievements")
    public ResponseEntity<List<AchievementDTO>> achievements() {
        return ResponseEntity.ok(achievementService.list(currentMemberService.getCurrentMember()));
    }

    /**
     * 서비스가 보관 중인 내 개인정보를 JSON으로 내려준다 (개인정보 열람권). 캐시하지 않는다.
     *
     * @return 개인정보
     */
    @Operation(summary = "내 정보 내려받기", description = "서비스가 보관 중인 내 개인정보를 JSON으로 제공합니다. (개인정보 열람권)")
    @GetMapping("/member/me/export")
    public ResponseEntity<Map<String, Object>> exportMyData() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(personalDataService.export(currentMemberService.getCurrentMember()));
    }

    /**
     * 비밀번호를 바꾼다. 모든 기기에서 로그아웃되고 RefreshToken 쿠키를 지운다.
     *
     * @param request 현재 비밀번호, 새 비밀번호
     * @return 204
     */
    @Operation(summary = "비밀번호 변경", description = "변경 시 모든 기기에서 로그아웃됩니다.")
    @PatchMapping("/member/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        memberService.changePassword(currentMemberService.getCurrentMember(), request);
        return ResponseEntity.noContent().headers(expiredCookies()).build();
    }

    /**
     * 닉네임을 바꾼다.
     *
     * @param request 새 닉네임
     * @return 204
     */
    @Operation(summary = "닉네임 변경")
    @PatchMapping("/member/nickname")
    public ResponseEntity<Void> changeNickname(@Valid @RequestBody NicknameChangeRequest request) {
        memberService.changeNickname(currentMemberService.getCurrentMember(), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 아이디·비밀번호 찾기에 쓰는 이름과 이메일을 등록하거나 바꾼다.
     *
     * @param request 이름, 이메일, 현재 비밀번호
     * @return 204
     */
    @Operation(summary = "이름·이메일 등록 / 변경", description = "아이디 찾기·비밀번호 찾기에 쓰는 정보입니다. 비밀번호가 있는 계정은 현재 비밀번호를 확인합니다.")
    @PatchMapping("/member/contact")
    public ResponseEntity<Void> changeContact(@Valid @RequestBody ContactChangeRequest request) {
        memberService.changeContact(currentMemberService.getCurrentMember(), request);
        return ResponseEntity.noContent().build();
    }

    /**
     * 회원 탈퇴 (소프트 삭제). 랭킹·도감은 익명으로 남고, AI 대화 기록은 즉시 지운다.
     *
     * @return 204
     */
    @Operation(summary = "회원 탈퇴", description = "소프트 삭제. 랭킹·도감 기록은 익명으로 보존됩니다.")
    @DeleteMapping("/member")
    public ResponseEntity<Void> withdraw() {
        memberService.withdraw(currentMemberService.getCurrentMember());
        return ResponseEntity.noContent().headers(expiredCookies()).build();
    }

    /**
     * 로그아웃. RefreshToken을 Redis에서 지우고 발급된 AccessToken을 폐기하며 쿠키를 지운다.
     *
     * @param request RefreshToken 쿠키를 읽을 요청
     * @return 204
     */
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
                    tokenRevocationService.revoke(loginId); // 발급된 AccessToken도 즉시 무효
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
