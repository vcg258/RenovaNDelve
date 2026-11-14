package com.renovandelve.game.dto.response;

import com.renovandelve.game.domain.ChronicleChapterEntity;

import java.time.LocalDateTime;

/**
 * 연대기의 한 장 (화면용).
 *
 * @param chapterNo  장 번호 (1부터)
 * @param title      장 제목
 * @param body       본문
 * @param cleared    클리어한 원정이면 true
 * @param stage      도달한 스테이지
 * @param node       도달한 노드
 * @param faction    기운 세력 (HEAVEN / ABYSS / HUMAN, 없으면 null)
 * @param difficulty 난이도
 * @param createdAt  기록 일시
 */
public record ChronicleChapterDTO(int chapterNo, String title, String body, boolean cleared, int stage, int node,
                                  String faction, String difficulty, LocalDateTime createdAt) {

    /**
     * 엔티티를 화면용으로 옮긴다.
     *
     * @param chapter 장
     * @return DTO
     */
    public static ChronicleChapterDTO from(ChronicleChapterEntity chapter) {
        return new ChronicleChapterDTO(chapter.getChapterNo(), chapter.getTitle(), chapter.getBody(), chapter.isCleared(),
                chapter.getStage(), chapter.getNode(), chapter.getFaction(), chapter.getDifficulty(),
                chapter.getCreatedAt());
    }
}
