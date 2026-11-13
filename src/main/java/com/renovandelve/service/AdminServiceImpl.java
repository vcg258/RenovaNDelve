package com.renovandelve.service;

import com.renovandelve.domain.MemberEntity;
import com.renovandelve.domain.RankingEntity;
import com.renovandelve.domain.gamestateentity.Difficulty;
import com.renovandelve.domain.gamestateentity.GameStateEntity;
import com.renovandelve.domain.memberentity.MemberStatus;
import com.renovandelve.domain.memberentity.Role;
import com.renovandelve.dto.request.MemberStatusChangeRequest;
import com.renovandelve.dto.request.RoleChangeRequest;
import com.renovandelve.dto.response.AdminStatsDTO;
import com.renovandelve.dto.response.MemberSummaryDTO;
import com.renovandelve.dto.response.PageResponse;
import com.renovandelve.dto.response.RankingViewDTO;
import com.renovandelve.repository.GameStateRepository;
import com.renovandelve.repository.MemberRepository;
import com.renovandelve.repository.MemberSpecifications;
import com.renovandelve.repository.RankingRepository;
import com.renovandelve.service.redis.GameSessionService;
import com.renovandelve.service.redis.TokenRevocationService;
import com.renovandelve.service.redis.SecurityTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Log4j2
@Service
@RequiredArgsConstructor
public class AdminServiceImpl implements AdminService {
    private final MemberRepository memberRepository;
    private final GameStateRepository gameStateRepository;
    private final RankingRepository rankingRepository;
    private final GameSessionService gameSessionService;
    private final SecurityTokenService securityTokenService;
    private final TokenRevocationService tokenRevocationService;

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
                MemberSummaryDTO.of(member, playingMemberIds.contains(member.getMemberId())));
    }

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
        return MemberSummaryDTO.of(target, false);
    }

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

        return MemberSummaryDTO.of(target, false);
    }

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
            topRankings.add(new RankingViewDTO(rank++, entity.getMember().displayName(), entity.getDifficulty(),
                    entity.getBestScore(), entity.getClearTime(), entity.getAchievedAt()));
        }

        return new AdminStatsDTO(total, active, suspended, withdrawn, recent,
                activeGames, allGames.size(), cleared, byDifficulty, topRankings);
    }

    /* HELPER METHODS */

    private MemberEntity findMember(Long memberId) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> new NoSuchElementException("회원을 찾을 수 없습니다. memberId=" + memberId));
    }

    private void guardSelf(MemberEntity target, MemberEntity actor, String message) {
        if (target.getMemberId().equals(actor.getMemberId())) {
            throw new IllegalStateException(message);
        }
    }

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
