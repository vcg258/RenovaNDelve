package com.renovandelve.member.controller;

import com.renovandelve.member.dto.request.MemberStatusChangeRequest;
import com.renovandelve.member.dto.request.RoleChangeRequest;
import com.renovandelve.member.dto.response.AdminStatsDTO;
import com.renovandelve.member.dto.response.MemberSummaryDTO;
import com.renovandelve.global.dto.response.PageResponse;
import com.renovandelve.member.service.AdminService;
import com.renovandelve.member.service.CurrentMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 운영자 전용 API.
 * SecurityConfig에서 /api/admin/** 은 ROLE_ADMIN 만 접근 가능하도록 통제된다.
 */
@Tag(name = "관리자", description = "회원 관리 및 운영 통계 (ROLE_ADMIN 전용)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final CurrentMemberService currentMemberService;

    /**
     * 회원 목록을 검색한다 (아이디·닉네임 키워드, 상태 필터, 페이지).
     *
     * @param keyword 아이디 또는 닉네임 일부
     * @param status  ACTIVE / SUSPENDED / WITHDRAWN (없으면 전체)
     * @param page    페이지 번호 (0부터)
     * @param size    페이지 크기
     * @return 회원 요약 페이지
     */
    @Operation(summary = "회원 목록 조회", description = "아이디/닉네임 검색과 상태 필터, 페이징을 지원합니다.")
    @GetMapping("/members")
    public ResponseEntity<PageResponse<MemberSummaryDTO>> getMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getMembers(keyword, status, page, size));
    }

    /**
     * 회원을 정지하거나 정지를 푼다. 정지하면 진행 중인 게임과 로그인이 즉시 끊긴다.
     * 자기 자신은 바꿀 수 없다.
     *
     * @param memberId 대상 회원
     * @param request  바꿀 상태와 사유
     * @return 바뀐 회원 요약
     */
    @Operation(summary = "회원 제재 / 제재 해제", description = "SUSPENDED로 변경 시 진행 중인 게임과 로그인 세션이 즉시 종료됩니다.")
    @PatchMapping("/members/{memberId}/status")
    public ResponseEntity<MemberSummaryDTO> changeStatus(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberStatusChangeRequest request) {
        return ResponseEntity.ok(
                adminService.changeStatus(memberId, request, currentMemberService.getCurrentMember()));
    }

    /**
     * 회원 권한을 바꾼다 (USER ↔ ADMIN). 자기 자신과 마지막 관리자는 바꿀 수 없다.
     *
     * @param memberId 대상 회원
     * @param request  바꿀 권한
     * @return 바뀐 회원 요약
     */
    @Operation(summary = "회원 권한 변경", description = "USER <-> ADMIN. 마지막 관리자는 강등할 수 없습니다.")
    @PatchMapping("/members/{memberId}/role")
    public ResponseEntity<MemberSummaryDTO> changeRole(
            @PathVariable Long memberId,
            @Valid @RequestBody RoleChangeRequest request) {
        return ResponseEntity.ok(
                adminService.changeRole(memberId, request, currentMemberService.getCurrentMember()));
    }

    /**
     * 회원의 진행 중인 게임을 강제로 끝낸다.
     *
     * @param memberId 대상 회원
     * @return 204
     */
    @Operation(summary = "회원의 진행 중인 게임 강제 종료")
    @PostMapping("/members/{memberId}/terminate-game")
    public ResponseEntity<Void> terminateGame(@PathVariable Long memberId) {
        adminService.terminateGame(memberId);
        return ResponseEntity.noContent().build();
    }

    /**
     * 운영 통계: 회원·게임 현황, 난이도 분포, 상위 랭킹.
     *
     * @return 통계
     */
    @Operation(summary = "운영 통계", description = "회원 현황, 게임 진행 현황, 난이도 분포, 상위 랭킹")
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDTO> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }
}
