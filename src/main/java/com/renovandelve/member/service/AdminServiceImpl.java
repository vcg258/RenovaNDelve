package com.renovandelve.member.service;

import com.renovandelve.ranking.mapper.RankingMapper;
import com.renovandelve.member.domain.MemberEntity;
import com.renovandelve.ranking.domain.RankingEntity;
import com.renovandelve.game.domain.gamestateentity.Difficulty;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.member.domain.memberentity.MemberStatus;
import com.renovandelve.member.domain.memberentity.Role;
import com.renovandelve.member.dto.request.MemberStatusChangeRequest;
import com.renovandelve.member.dto.request.RoleChangeRequest;
import com.renovandelve.member.dto.response.AdminStatsDTO;
import com.renovandelve.member.dto.response.MemberSummaryDTO;
import com.renovandelve.global.dto.response.PageResponse;
import com.renovandelve.ranking.dto.response.RankingViewDTO;
import com.renovandelve.game.repository.GameStateRepository;
import com.renovandelve.member.repository.MemberRepository;
import com.renovandelve.member.repository.MemberSpecifications;
import com.renovandelve.ranking.repository.RankingRepository;
import com.renovandelve.game.service.redis.GameSessionService;
import com.renovandelve.auth.service.redis.TokenRevocationService;
import com.renovandelve.auth.service.redis.SecurityTokenService;
import com.renovandelve.member.mapper.MemberMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final MemberRepository memberRepository;
    private final GameStateRepository gameStateRepository;
    private final RankingRepository rankingRepository;
    private final GameSessionService gameSessionService;
    private final SecurityTokenService securityTokenService;
    private final TokenRevocationService tokenRevocationService;
    private final RankingMapper rankingMapper;
    private final MemberMapper memberMapper;

    /**
     * 회원 목록을 최근 가입 순으로 검색한다. 페이지 크기는 1~100으로 제한한다.
     *
     * @param keyword 아이디 또는 닉네임 일부
     * @param status  계정 상태 (없으면 전체)
     * @param page    페이지 번호
     * @param size    페이지 크기
     * @return 회원 요약 페이지
     */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<MemberSummaryDTO> getMembers(String keyword, String status, int page, int size) {
        MemberStatus statusFilter = parseStatus(status, true);
        String keywordFilter = (keyword == null || keyword.isBlank()) ? null : keyword.trim();

        Page<MemberEntity> result = memberRepository.findAll(
                MemberSpecifications.search(keywordFilter, statusFilter),
                PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)),
                        Sort.by(Sort.Direction.DESC, "createdAt")));

        // 진행 중인 게임 보유 여부를 한 번에 조회해 N+1 방지
        Set<Long> playingMemberIds = new HashSet<>();
        gameStateRepository.findByIsActiveTrue()
                .forEach(game -> playingMemberIds.add(game.getMember().getMemberId()));

        return PageResponse.of(result, member ->
                memberMapper.toSummaryDTO(member, playingMemberIds.contains(member.getMemberId())));
    }

    /**
     * 회원을 정지하거나 정지를 푼다. 정지하면 RefreshToken과 AccessToken을 즉시 폐기하고 진행 중인 게임을 끝낸다.
     * 자기 자신, 탈퇴 회원, 관리자는 정지할 수 없다.
     *
     * @param targetMemberId 대상 회원
     * @param request        바꿀 상태와 사유
     * @param actor          처리하는 관리자
     * @return 바뀐 회원 요약
     */
    @Override
    @Transactional
    public MemberSummaryDTO changeStatus(Long targetMemberId, MemberStatusChangeRequest request, MemberEntity actor) {
        MemberEntity target = findMember(targetMemberId);
        guardSelf(target, actor, "자기 자신의 상태는 변경할 수 없습니다.");

        MemberStatus newStatus = parseStatus(request.status(), false);
        if (newStatus == MemberStatus.WITHDRAWN) {
            throw new IllegalArgumentException("탈퇴 처리는 회원 본인만 할 수 있습니다.");
        }
        if (target.getStatus() == MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("이미 탈퇴한 회원입니다.");
        }
        if (target.isAdmin() && newStatus == MemberStatus.SUSPENDED) {
            throw new IllegalStateException("관리자 계정은 정지할 수 없습니다. 권한을 먼저 회수해주세요.");
        }

        if (newStatus == MemberStatus.SUSPENDED) {
            String reason = (request.reason() == null || request.reason().isBlank())
                    ? "운영 정책 위반" : request.reason().trim();
            target.suspend(reason);
            // 정지 즉시 세션을 끊는다 (RefreshToken 폐기 + 진행 중 게임 종료)
            securityTokenService.logout(target.getLoginId());
            tokenRevocationService.revoke(target.getLoginId()); // 발급된 AccessToken도 즉시 무효
            gameStateRepository.findByMember_MemberIdAndIsActiveTrue(target.getMemberId())
                    .ifPresent(game -> {
                        game.deactivate();
                        gameStateRepository.save(game);
                        gameSessionService.delete(game.getGameStateId());
                    });
            log.info("[ADMIN] 회원 정지 target={} by={} reason={}", target.getLoginId(), actor.getLoginId(), reason);
        } else {
            target.activate();
            log.info("[ADMIN] 회원 정지 해제 target={} by={}", target.getLoginId(), actor.getLoginId());
        }

        memberRepository.save(target);
        return memberMapper.toSummaryDTO(target, false);
    }

    /**
     * 회원 권한을 바꾼다. 자기 자신과 마지막 관리자는 바꿀 수 없고, 바뀐 회원의 토큰은 즉시 폐기해 재로그인하게 한다.
     *
     * @param targetMemberId 대상 회원
     * @param request        바꿀 권한
     * @param actor          처리하는 관리자
     * @return 바뀐 회원 요약
     */
    @Override
    @Transactional
    public MemberSummaryDTO changeRole(Long targetMemberId, RoleChangeRequest request, MemberEntity actor) {
        MemberEntity target = findMember(targetMemberId);
        guardSelf(target, actor, "자기 자신의 권한은 변경할 수 없습니다.");

        Role newRole;
        try {
            newRole = Role.valueOf(request.role().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 권한입니다: " + request.role());
        }
        if (target.getStatus() == MemberStatus.WITHDRAWN) {
            throw new IllegalStateException("탈퇴한 회원의 권한은 변경할 수 없습니다.");
        }

        // 마지막 관리자를 강등시켜 아무도 관리할 수 없게 되는 상황 방지
        if (target.isAdmin() && newRole == Role.USER && memberRepository.countByRole(Role.ADMIN) <= 1) {
            throw new IllegalStateException("마지막 관리자는 강등할 수 없습니다.");
        }

        target.changeRole(newRole);
        memberRepository.save(target);

        // 권한이 바뀌면 기존 토큰의 role 클레임이 낡으므로 재로그인을 유도
        securityTokenService.logout(target.getLoginId());
        tokenRevocationService.revoke(target.getLoginId()); // 발급된 AccessToken도 즉시 무효
        log.info("[ADMIN] 권한 변경 target={} -> {} by={}", target.getLoginId(), newRole, actor.getLoginId());

        return memberMapper.toSummaryDTO(target, false);
    }

    /**
     * 회원의 진행 중인 게임을 강제로 끝내고 Redis 세션을 지운다.
     *
     * @param targetMemberId 대상 회원
     * @throws java.util.NoSuchElementException 진행 중인 게임이 없으면
     */
    @Override
    @Transactional
    public void terminateGame(Long targetMemberId) {
        GameStateEntity game = gameStateRepository.findByMember_MemberIdAndIsActiveTrue(targetMemberId)
                .orElseThrow(() -> new NoSuchElementException("진행 중인 게임이 없습니다."));
        game.deactivate();
        gameStateRepository.save(game);
        gameSessionService.delete(game.getGameStateId());
        log.info("[ADMIN] 게임 강제 종료 memberId={} gameStateId={}", targetMemberId, game.getGameStateId());
    }

    /**
     * 운영 통계: 회원 수(상태별, 최근 7일 가입), 게임 수, 난이도 분포, 상위 랭킹.
     *
     * @return 통계
     */
    @Override
    @Transactional(readOnly = true)
    public AdminStatsDTO getStats() {
        long total = memberRepository.count();
        long active = memberRepository.countByStatus(MemberStatus.ACTIVE);
        long suspended = memberRepository.countByStatus(MemberStatus.SUSPENDED);
        long withdrawn = memberRepository.countByStatus(MemberStatus.WITHDRAWN);
        long recent = memberRepository.countByCreatedAtAfter(LocalDateTime.now().minusDays(7));

        List<GameStateEntity> allGames = gameStateRepository.findAll();
        long activeGames = allGames.stream().filter(g -> Boolean.TRUE.equals(g.getIsActive())).count();
        long cleared = allGames.stream()
                .filter(g -> g.getCurrentStage() >= 5 && g.getCurrentNodeId() != null && g.getCurrentNodeId() >= 10)
                .count();

        Map<String, Long> byDifficulty = new LinkedHashMap<>();
        for (Difficulty difficulty : Difficulty.values()) {
            byDifficulty.put(difficulty.name(),
                    allGames.stream().filter(g -> g.getDifficulty() == difficulty).count());
        }

        List<RankingViewDTO> topRankings = new ArrayList<>();
        int rank = 1;
        for (RankingEntity entity : rankingRepository.findTop10ByDifficultyOrderByBestScoreDesc(Difficulty.NORMAL)) {
            topRankings.add(rankingMapper.toViewDTO(entity, rank++));
        }

        return new AdminStatsDTO(total, active, suspended, withdrawn, recent,
                activeGames, allGames.size(), cleared, byDifficulty, topRankings);
    }

    /* HELPER METHODS */

    /**
     * 회원을 찾는다.
     *
     * @param memberId 회원 ID
     * @return 회원
     * @throws java.util.NoSuchElementException 없으면
     */
    private MemberEntity findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다. memberId=" + memberId));
    }

    /**
     * 대상이 처리하는 관리자 본인이면 거부한다.
     *
     * @param target  대상 회원
     * @param actor   처리하는 관리자
     * @param message 거부 문구
     * @throws IllegalStateException 본인이면
     */
    private void guardSelf(MemberEntity target, MemberEntity actor, String message) {
        if (target.getMemberId().equals(actor.getMemberId())) {
            throw new IllegalStateException(message);
        }
    }

    /**
     * 상태 문자열을 계정 상태로 바꾼다.
     *
     * @param status   상태 문자열
     * @param nullable true면 비어 있을 때 null을 돌려준다
     * @return 계정 상태
     * @throws IllegalArgumentException 비어 있는데 필수이거나 알 수 없는 값이면
     */
    private MemberStatus parseStatus(String status, boolean nullable) {
        if (status == null || status.isBlank()) {
            if (nullable) return null;
            throw new IllegalArgumentException("상태를 지정해주세요.");
        }
        try {
            return MemberStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("알 수 없는 상태입니다: " + status);
        }
    }
}
