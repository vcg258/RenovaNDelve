package com.renovandelve.controller;

import com.renovandelve.dto.request.MemberStatusChangeRequest;
import com.renovandelve.dto.request.RoleChangeRequest;
import com.renovandelve.dto.response.AdminStatsDTO;
import com.renovandelve.dto.response.MemberSummaryDTO;
import com.renovandelve.dto.response.PageResponse;
import com.renovandelve.service.AdminService;
import com.renovandelve.service.CurrentMemberService;
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

    @Operation(summary = "회원 목록 조회", description = "아이디/닉네임 검색과 상태 필터, 페이징을 지원합니다.")
    @GetMapping("/members")
    public ResponseEntity<PageResponse<MemberSummaryDTO>> getMembers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminService.getMembers(keyword, status, page, size));
    }

    @Operation(summary = "회원 제재 / 제재 해제", description = "SUSPENDED로 변경 시 진행 중인 게임과 로그인 세션이 즉시 종료됩니다.")
    @PatchMapping("/members/{memberId}/status")
    public ResponseEntity<MemberSummaryDTO> changeStatus(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberStatusChangeRequest request) {
        return ResponseEntity.ok(
                adminService.changeStatus(memberId, request, currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "회원 권한 변경", description = "USER <-> ADMIN. 마지막 관리자는 강등할 수 없습니다.")
    @PatchMapping("/members/{memberId}/role")
    public ResponseEntity<MemberSummaryDTO> changeRole(
            @PathVariable Long memberId,
            @Valid @RequestBody RoleChangeRequest request) {
        return ResponseEntity.ok(
                adminService.changeRole(memberId, request, currentMemberService.getCurrentMember()));
    }

    @Operation(summary = "회원의 진행 중인 게임 강제 종료")
    @PostMapping("/members/{memberId}/terminate-game")
    public ResponseEntity<Void> terminateGame(@PathVariable Long memberId) {
        adminService.terminateGame(memberId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "운영 통계", description = "회원 현황, 게임 진행 현황, 난이도 분포, 상위 랭킹")
    @GetMapping("/stats")
    public ResponseEntity<AdminStatsDTO> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }
}
