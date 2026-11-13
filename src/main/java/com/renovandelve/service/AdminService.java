package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.dto.request.MemberStatusChangeRequest;
import com.renovandelve.dto.request.RoleChangeRequest;
import com.renovandelve.dto.response.AdminStatsDTO;
import com.renovandelve.dto.response.MemberSummaryDTO;
import com.renovandelve.dto.response.PageResponse;

public interface AdminService {

    /** 회원 목록 조회 (검색 + 상태 필터 + 페이징) */
    PageResponse<MemberSummaryDTO> getMembers(String keyword, String status, int page, int size);

    /** 회원 제재 / 제재 해제 */
    MemberSummaryDTO changeStatus(Long targetMemberId, MemberStatusChangeRequest request, MemberEntity actor);

    /** 회원 권한 변경 */
    MemberSummaryDTO changeRole(Long targetMemberId, RoleChangeRequest request, MemberEntity actor);

    /** 회원의 진행 중인 게임 강제 종료 */
    void terminateGame(Long targetMemberId);

    /** 대시보드 통계 */
    AdminStatsDTO getStats();
}
