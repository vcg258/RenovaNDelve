package com.renovandelve.game.service;

import com.renovandelve.ai.dto.response.RunReviewDTO;
import com.renovandelve.game.domain.ChronicleChapterEntity;
import com.renovandelve.game.domain.gamestateentity.GameStateEntity;
import com.renovandelve.game.dto.response.ChronicleChapterDTO;
import com.renovandelve.game.engine.Faction;
import com.renovandelve.game.repository.ChronicleChapterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 레노바 연대기: 끝난 원정마다 한 장씩 쌓이는 이야기책.
 *
 * 장의 제목과 본문은 런 회고와 같은 AI 호출에서 함께 받는다(호출 수가 늘지 않는다).
 * 앞선 장 몇 개를 그 호출의 입력으로 넣어, 원정들이 하나의 이야기로 이어지게 한다.
 * AI가 장을 쓰지 못하면 서버가 사실만으로 짧은 장을 쓴다. 연대기에 빈 장은 생기지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChronicleService {

    /** AI에게 앞 이야기로 넘길 때 장 본문을 자르는 길이 */
    private static final int PREVIOUS_BODY_LIMIT = 160;

    private final ChronicleChapterRepository chronicleChapterRepository;

    /**
     * 다음 장을 쓰기 위한 앞 이야기. 최근 3장을 오래된 것부터.
     *
     * @param memberId 회원 ID
     * @return "제N장 「제목」 본문" 줄 목록 (첫 원정이면 비어 있음)
     */
    @Transactional(readOnly = true)
    public List<String> previousChapters(Long memberId) {
        List<ChronicleChapterEntity> recent = new ArrayList<>(chronicleChapterRepository.findTop3ByMemberIdOrderByChapterNoDesc(memberId));
        Collections.reverse(recent);
        return recent.stream()
                .map(c -> "제%d장 「%s」 %s".formatted(c.getChapterNo(), c.getTitle(), cut(c.getBody(), PREVIOUS_BODY_LIMIT)))
                .toList();
    }

    /**
     * 끝난 원정을 연대기의 다음 장으로 남긴다. 이미 장이 있으면 그대로 둔다.
     *
     * @param gameState 끝난 원정
     * @param cleared   클리어했으면 true
     * @param review    런 회고 (장 제목·본문 포함). 없거나 장이 비었으면 서버가 쓴다
     * @return 남긴 장 (이미 있었으면 null)
     */
    @Transactional
    public ChronicleChapterEntity record(GameStateEntity gameState, boolean cleared, RunReviewDTO review) {
        if (chronicleChapterRepository.existsByGameStateId(gameState.getGameStateId())) return null;

        Long memberId = gameState.getMember().getMemberId();
        int chapterNo = (int) chronicleChapterRepository.countByMemberId(memberId) + 1;
        int stage = gameState.getCurrentStage();
        int node = gameState.getCurrentNodeId() == null ? 0 : gameState.getCurrentNodeId().intValue();
        Faction faction = gameState.alignment().dominant();

        boolean written = review != null && notBlank(review.chapterTitle()) && notBlank(review.chapter());
        ChronicleChapterEntity chapter = ChronicleChapterEntity.builder()
                .memberId(memberId)
                .gameStateId(gameState.getGameStateId())
                .chapterNo(chapterNo)
                .title(written ? cut(review.chapterTitle(), 40) : fallbackTitle(cleared, stage))
                .body(written ? review.chapter().trim() : fallbackBody(cleared, stage, node, faction))
                .cleared(cleared)
                .stage(stage)
                .node(node)
                .faction(faction == null ? null : faction.name())
                .difficulty(gameState.getDifficulty().name())
                .build();
        chronicleChapterRepository.save(chapter);
        log.info("연대기 기록 memberId={} 제{}장 aiWritten={}", memberId, chapterNo, written);
        return chapter;
    }

    /**
     * 회원의 연대기 전체.
     *
     * @param memberId 회원 ID
     * @return 1장부터
     */
    @Transactional(readOnly = true)
    public List<ChronicleChapterDTO> chapters(Long memberId) {
        return chronicleChapterRepository.findByMemberIdOrderByChapterNoAsc(memberId).stream()
                .map(ChronicleChapterDTO::from)
                .toList();
    }

    /** AI가 장을 쓰지 못했을 때의 제목 */
    static String fallbackTitle(boolean cleared, int stage) {
        return cleared ? "문 앞에 선 자" : "스테이지 %d의 침묵".formatted(stage);
    }

    /** AI가 장을 쓰지 못했을 때의 본문. 서버가 아는 사실만 쓴다 */
    static String fallbackBody(boolean cleared, int stage, int node, Faction faction) {
        String leaning = faction == null ? "어느 쪽에도 기대지 않은 채"
                : "%s의 방식에 기대어".formatted(faction.label());
        return cleared
                ? "한 인간이 %s 다섯 층을 내려가 마계의 문 앞에 섰다. 왕은 쓰러졌고, 이 장은 거기서 닫힌다.".formatted(leaning)
                : "한 인간이 %s 내려갔으나 스테이지 %d의 %d번째 걸음에서 멈췄다. 그 자리에는 아직 그의 그림자가 남아 있다."
                    .formatted(leaning, stage, node);
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String cut(String value, int max) {
        String trimmed = value == null ? "" : value.trim().replaceAll("\\s+", " ");
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max) + "…";
    }
}
